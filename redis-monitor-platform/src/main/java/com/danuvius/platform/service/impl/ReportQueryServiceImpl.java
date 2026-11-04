package com.danuvius.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.danuvius.platform.common.constant.CommonConstant;
import com.danuvius.platform.mapper.AlarmRecordMapper;
import com.danuvius.platform.mapper.MetricLogMapper;
import com.danuvius.platform.mapper.RedisInstanceMapper;
import com.danuvius.platform.mapper.RedisSwitchEventMapper;
import com.danuvius.platform.model.dto.alarm.record.AlarmStatsDTO;
import com.danuvius.platform.model.dto.alarm.record.ReportOverviewStatsDTO;
import com.danuvius.platform.model.dto.alarm.record.SwitchStatsDTO;
import com.danuvius.platform.model.dto.monitor.*;
import com.danuvius.platform.model.entity.RedisInstance;
import com.danuvius.platform.model.enums.instance.SwitchTypeEnum;
import com.danuvius.platform.model.enums.instance.ManagementStatusEnum;
import com.danuvius.platform.service.ReportQueryService;
import com.danuvius.platform.util.CacheKeyUtil;
import com.danuvius.platform.util.TimeBucketUtil;
import com.github.benmanes.caffeine.cache.Cache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * 日周报数据查询与组装实现
 */
@Slf4j
@Service
public class ReportQueryServiceImpl implements ReportQueryService {

    private final RedisInstanceMapper redisInstanceMapper;
    private final RedisSwitchEventMapper switchEventMapper;
    private final AlarmRecordMapper alarmRecordMapper;
    private final MetricLogMapper metricLogMapper;
    private final Cache<String, Object> caffeineCache;

    public ReportQueryServiceImpl(RedisInstanceMapper redisInstanceMapper,
                                  RedisSwitchEventMapper switchEventMapper,
                                  AlarmRecordMapper alarmRecordMapper,
                                  MetricLogMapper metricLogMapper,
                                  Cache<String, Object> caffeineCache) {
        this.redisInstanceMapper = redisInstanceMapper;
        this.switchEventMapper = switchEventMapper;
        this.alarmRecordMapper = alarmRecordMapper;
        this.metricLogMapper = metricLogMapper;
        this.caffeineCache = caffeineCache;
    }

    /**
     * 日周报查询
     * 当天=00:00~当前时刻、近7天=前推6天共7自然日，
     */
    @Override
    public ReportQueryResponse queryReport(ReportQueryRequest req) {
        String key = CacheKeyUtil.report(req.getInstanceId(), req.getReportType());
        Object cached = caffeineCache.getIfPresent(key);
        if (cached != null) {
            return (ReportQueryResponse) cached;
        }

        ReportQueryResponse resp = buildReportData(req.getInstanceId(), req.getReportType());

        caffeineCache.put(key, resp);
        return resp;
    }

    /**
     * 组装完整报告数据，查询与导出共用
     */
    @Override
    public ReportQueryResponse buildReportData(String instanceId, String reportType) {
        boolean daily = !CommonConstant.REPORT_TYPE_WEEK.equals(reportType);
        LocalDateTime endTime = LocalDateTime.now();
        // 日报：00:00 至当前时刻； 周报：当前日期向前推6天到当天共7自然日
        LocalDateTime startTime = daily
                ? endTime.toLocalDate().atStartOfDay()
                : endTime.toLocalDate().minusDays(6).atStartOfDay();


        ReportQueryResponse resp = new ReportQueryResponse();

        // 概览统计
        ReportOverviewStatsDTO stats = metricLogMapper.selectReportOverviewStats(
                instanceId, startTime, endTime);

        if (stats == null) {
            stats = new ReportOverviewStatsDTO();
        }


        ReportOverviewDTO ov = new ReportOverviewDTO();
        ov.setCpuAvg(toText(stats.getCpuAvg()));
        ov.setCpuMax(toText(stats.getCpuMax()));
        ov.setCpuMaxTim(stats.getCpuMaxTim());
        ov.setRedisMemAvg(toText(stats.getRedisMemAvg()));
        ov.setRedisMemMax(toText(stats.getRedisMemMax()));
        ov.setRedisMemMaxTim(stats.getRedisMemMaxTim());
        ov.setQpsAvg(toText(stats.getQpsAvg()));
        ov.setQpsMax(toText(stats.getQpsMax()));
        ov.setQpsMaxTim(stats.getQpsMaxTim());
        ov.setLatencyAvg(toText(stats.getLatencyAvg()));
        ov.setLatencyMax(toText(stats.getLatencyMax()));
        ov.setLatencyMaxTim(stats.getLatencyMaxTim());

        // 告警统计（按时间范围，可按实例过滤）
        AlarmStatsDTO alarmStats = alarmRecordMapper.selectReportAlarmStats(
                instanceId, startTime, endTime);

        if (alarmStats == null) {
            alarmStats = new AlarmStatsDTO();
            alarmStats.setAlarmTotal(0L);
            alarmStats.setInfoCount(0L);
            alarmStats.setAlarmCount(0L);
            alarmStats.setSeverityCount(0L);

        }
        ov.setAlarmStatsDTO(alarmStats);


        // 主从切换统计
        SwitchStatsDTO switchStats = switchEventMapper.selectSwitchStats(startTime, endTime);

        if (switchStats == null) {
            switchStats = new SwitchStatsDTO();
            switchStats.setSwitchTotal(0L);
            switchStats.setLastSwitchTime(null);
        }
        ov.setSwitchTotal(switchStats.getSwitchTotal());
        ov.setLastSwitchTime(switchStats.getLastSwitchTime());

        // 节点可用率/节点数据完整度：实际采集点数 / 理论采集点数（5分钟一个采集周期）
        long collectCount = stats.getCollectCount();
        long theoryMinutes = ChronoUnit.MINUTES.between(startTime, endTime);
        long theoryCount = Math.max(1L, theoryMinutes / 5);
        BigDecimal rate = BigDecimal.valueOf(collectCount)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(theoryCount), 2, RoundingMode.HALF_UP);
        if (rate.compareTo(BigDecimal.valueOf(100)) > 0) {
            rate = BigDecimal.valueOf(100);
        }
        ov.setServiceAvailableRate(rate);

        // 主/从节点数（集群口径）
        List<RedisInstance> allInstances = selectActiveInstances(ManagementStatusEnum.ACTIVE.getCode());
        long masterCount = 0;
        long replicaCount = 0;
        if (!allInstances.isEmpty()) {
            for (RedisInstance instance : allInstances) {
                if (CommonConstant.DEFAULT_ROLE_MASTER.equalsIgnoreCase(instance.getRole())) {
                    masterCount++;
                } else {
                    replicaCount++;
                }
            }
        }
        ov.setMasterCount(masterCount);
        ov.setReplicaCount(replicaCount);

        resp.setOverview(ov);

        // 4项指标趋势序列
        // 当天按小时聚合，近7天按天聚合
        int bucketMinutes = daily ? 60 : 1440;
        boolean truncateDay = !daily;
        List<Map<String, Object>> trendRows = metricLogMapper.selectReportTrend(
                instanceId, startTime, endTime, bucketMinutes, truncateDay);
        resp.setTrendList(buildReportTrends(trendRows, startTime, endTime, bucketMinutes));

        // 告警与切换事件合并列表
        List<Map<String, Object>> alarmRows = alarmRecordMapper.selectReportEventList(
                instanceId, startTime, endTime, 500);

        List<Map<String, Object>> switchRows = switchEventMapper.selectSwitchEventList(
                startTime, endTime, 500);
        resp.setEventList(buildReportEvents(alarmRows, switchRows));

        return resp;
    }

    /**
     * 组装4项指标趋势序列，时间轴对齐、缺失补 null
     */
    private List<ReportTrendDTO> buildReportTrends(List<Map<String, Object>> rows,
                                                   LocalDateTime start, LocalDateTime end,
                                                   int bucketMinutes) {
        List<ReportTrendDTO> trends = new ArrayList<>();
        List<String> timestampList = buildAlignedTimestamps(start, end, bucketMinutes);

        String[][] metricDefs = {
                {"cpuUsage", "CPU使用率", "%"},
                {"redisMemUsed", "Redis内存使用量", "GB"},
                {"qps", "QPS", "次/秒"},
                {"avgLatency", "平均延迟", "μs"}
        };

        // bucket -> 各指标值
        Map<String, Map<String, Object>> bucketMap = new HashMap<>();
        if (rows != null) {
            for (Map<String, Object> row : rows) {
                bucketMap.put(formatBucket(row.get("bucket")), row);
            }
        }

        for (String[] def : metricDefs) {
            ReportTrendDTO trend = new ReportTrendDTO();
            trend.setMetricKey(def[0]);
            trend.setMetricName(def[1]);
            trend.setUnit(def[2]);
            List<Double> values = new ArrayList<>();
            for (String ts : timestampList) {
                Map<String, Object> row = bucketMap.get(ts);
                values.add(row == null ? null : toDouble(row.get(def[0])));
            }
            trend.setTimestampList(new ArrayList<>(timestampList));
            trend.setValueList(values);
            trends.add(trend);
        }
        return trends;
    }

    /**
     * 合并告警与主从切换明细，按触发时间倒序，补充节点展示字段
     */
    private List<ReportEventDTO> buildReportEvents(List<Map<String, Object>> alarmRows,
                                                   List<Map<String, Object>> switchRows) {
        List<ReportEventDTO> events = new ArrayList<>();
        if (alarmRows != null) {
            for (Map<String, Object> row : alarmRows) {
                ReportEventDTO event = new ReportEventDTO();
                event.setEventType(CommonConstant.ALARM);
                String instanceName = (String) row.get("instanceName");
                event.setNodeDisplay((instanceName == null ? "" : instanceName));
                event.setContent(str(row.get("alarmContent")));
                event.setHappenTime(toLocalDateTime(row.get("triggerTime")));
                event.setStatus(CommonConstant.RECOVER.equals(row.get("recoverStatus")) ? CommonConstant.RECOVER_DEC : CommonConstant.UNRECOVER_DEC);
                events.add(event);
            }
        }
        if (switchRows != null) {
            for (Map<String, Object> row : switchRows) {
                ReportEventDTO event = new ReportEventDTO();
                event.setEventType(CommonConstant.MASTER_SWITCH_DESC);
                event.setNodeDisplay(row.get("oldInstanceName") + " → " + row.get("newInstanceName"));
                event.setContent(event.getEventType()+"：" + event.getNodeDisplay()
                        + "（" + SwitchTypeEnum.descOf(str(row.get("switchType"))) + "）");
                event.setHappenTime(toLocalDateTime(row.get("switchTime")));
                event.setStatus(CommonConstant.SWITCH_SUCCESS.equals(row.get("switchResult")) ? CommonConstant.SUCCESS : CommonConstant.FAILURE);
                events.add(event);
            }
        }
        // 按发生时间倒序
        events.sort((a, b) -> {
            if (a.getHappenTime() == null) {
                return 1;
            }
            if (b.getHappenTime() == null) {
                return -1;
            }
            return b.getHappenTime().compareTo(a.getHappenTime());
        });
        return events;
    }


    private Double toDouble(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Double) {
            return (Double) value;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        try {
            return Double.valueOf(value.toString().trim());
        } catch (Exception e) {
            return null;
        }
    }

    /** BigDecimal 转字符串，保留数据库原始精度；无数据返回 null，前端展示 "--" */
    private String toText(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros().toPlainString();
    }


    private String str(Object value) {
        return value == null ? null : value.toString();
    }

    private LocalDateTime toLocalDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime) {
            return (LocalDateTime) value;
        }
        if (value instanceof Timestamp) {
            return ((Timestamp) value).toLocalDateTime();
        }
        if (value instanceof Date) {
            return new Timestamp(((Date) value).getTime()).toLocalDateTime();
        }
        try {
            return LocalDateTime.parse(value.toString().replace(" ", "T"));
        } catch (Exception e) {
            return null;
        }
    }

    /** 查询纳管中的实例 */
    private List<RedisInstance> selectActiveInstances(String managementStatus) {
        List<RedisInstance> instances = redisInstanceMapper.selectList(new LambdaQueryWrapper<RedisInstance>()
                .eq(RedisInstance::getManagementStatus,managementStatus)
        );
        return instances;
    }

    /** 时间轴对齐，与实时趋势共用同一套分桶口径 */
    private List<String> buildAlignedTimestamps(LocalDateTime start, LocalDateTime end, int bucketMinutes) {
        return TimeBucketUtil.alignedTimestamps(start, end, bucketMinutes);
    }

    /** 分桶向下取整，与实时趋势共用同一套分桶口径 */
    private LocalDateTime truncateToBucket(LocalDateTime time, int bucketMinutes) {
        return TimeBucketUtil.truncateToBucket(time, bucketMinutes);
    }

    /** 统一时间桶格式 */
    private String formatBucket(Object bucket) {
        return TimeBucketUtil.formatBucket(bucket);
    }
}
