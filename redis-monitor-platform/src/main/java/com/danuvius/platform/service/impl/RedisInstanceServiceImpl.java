package com.danuvius.platform.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.danuvius.platform.common.constant.CommonConstant;
import com.danuvius.platform.common.exception.BusinessCode;
import com.danuvius.platform.common.exception.BusinessException;
import com.danuvius.platform.mapper.*;
import com.danuvius.platform.model.dto.alarm.record.AlarmStatsDTO;
import com.danuvius.platform.model.dto.alarm.record.ReportOverviewStatsDTO;
import com.danuvius.platform.model.dto.alarm.record.SwitchStatsDTO;
import com.danuvius.platform.model.dto.metric.MetricCurrentValueDTO;
import com.danuvius.platform.model.dto.monitor.*;
import com.danuvius.platform.model.entity.AlarmRule;
import com.danuvius.platform.model.entity.MetricLog;
import com.danuvius.platform.model.entity.RedisInstance;
import com.danuvius.platform.model.entity.RedisSwitchEvent;
import com.danuvius.platform.model.enums.rule.AlarmLevelEnum;
import com.danuvius.platform.model.enums.metric.MetricTypeEnum;
import com.danuvius.platform.model.enums.StatusEnum;
import com.danuvius.platform.model.enums.TypeEnum;
import com.danuvius.platform.model.enums.instance.RunStatusEnum;
import com.danuvius.platform.service.AsyncTaskRunner;
import com.danuvius.platform.service.AuditLogService;
import com.danuvius.platform.service.RedisInstanceService;
import com.danuvius.platform.service.RedisSwitchEventService;
import com.danuvius.platform.util.*;
import com.github.benmanes.caffeine.cache.Cache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Slf4j
@Service
public class RedisInstanceServiceImpl extends ServiceImpl<RedisInstanceMapper, RedisInstance>
        implements RedisInstanceService {
    /** 复制延迟指标 key，与前端/规则保持一致 */
    private static final String REPL_DELAY_METRIC_KEY = "replDelay";
    private final RedisInstanceService redisInstanceService;

    /* 实例离线判定阈值：超过该时长无心跳视为离线，平台端+执行器架构下按心跳时长判定，默认600s，可配置） */
    @Value("${platform.instance.offline-threshold-seconds:600}")
    private long offlineThresholdSeconds;

    private final RedisInstanceMapper redisInstanceMapper;
    private final RedisSwitchEventMapper switchEventMapper;
    private final AlarmRuleMapper alarmRuleMapper;
    private final AlarmRecordMapper alarmRecordMapper;
    private final MetricLogMapper metricLogMapper;
    private final RuleMatchHelper  ruleMatchHelper;
    /* 报告导出记操作审计日志 */
    private final AuditLogService auditLogService;
    private final RedisSwitchEventService redisSwitchEventService;
    /* 报告导出异步任务存储 */
    private final ReportExportTaskHolder taskHolder;
    /* 报告导出文件生成器（异步任务内使用） */
    private final ReportFileGenerator reportFileGenerator;
    /* 通用异步任务执行器（独立Bean保证@Async生效） */
    private final AsyncTaskRunner asyncTaskRunner;


    private final Cache<String, Object> caffeineCache;

    public RedisInstanceServiceImpl(RedisInstanceMapper redisInstanceMapper,
                                    RedisSwitchEventMapper switchEventMapper,
                                    AlarmRuleMapper alarmRuleMapper,
                                    AlarmRecordMapper alarmRecordMapper,
                                    MetricLogMapper metricLogMapper,
                                    RuleMatchHelper ruleMatchHelper,
                                    AuditLogService auditLogService,
                                    RedisSwitchEventService redisSwitchEventService,
                                    ReportExportTaskHolder taskHolder,
                                    ReportFileGenerator reportFileGenerator,
                                    AsyncTaskRunner asyncTaskRunner,
                                    Cache<String, Object> caffeineCache,
                                    RedisInstanceService redisInstanceService) {
        this.redisInstanceMapper = redisInstanceMapper;
        this.switchEventMapper = switchEventMapper;
        this.alarmRuleMapper = alarmRuleMapper;
        this.alarmRecordMapper = alarmRecordMapper;
        this.metricLogMapper = metricLogMapper;
        this.ruleMatchHelper = ruleMatchHelper;
        this.auditLogService = auditLogService;
        this.redisSwitchEventService = redisSwitchEventService;
        this.taskHolder = taskHolder;
        this.reportFileGenerator = reportFileGenerator;
        this.asyncTaskRunner = asyncTaskRunner;
        this.caffeineCache = caffeineCache;
        this.redisInstanceService = redisInstanceService;
    }

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 查询实例列表。
     */
    @Override
    public List<RedisInstanceItemDTO> getInstanceList(String managementStatus) {
        String key = CacheKeyUtil.instanceList(managementStatus);
        Object cached = caffeineCache.getIfPresent(key);
        if (cached != null) {
            return (List<RedisInstanceItemDTO>) cached;
        }

        List<RedisInstance> instanceList = redisInstanceMapper.selectList(new LambdaQueryWrapper<RedisInstance>()
                .select(RedisInstance::getInstanceId,
                        RedisInstance::getInstanceName,
                        RedisInstance::getHostIp,
                        RedisInstance::getRedisPort,
                        RedisInstance::getRole,
                        RedisInstance::getRedisVersion,
                        RedisInstance::getRunStatus,
                        RedisInstance::getLastHeartbeatTime,
                        RedisInstance::getMasterHostIp,
                        RedisInstance::getMasterRedisPort
                        )
                .eq(RedisInstance::getManagementStatus, managementStatus)
                .last("ORDER BY CASE role WHEN 'MASTER' THEN 0 ELSE 1 END, instance_name ASC")
        );


        List<RedisInstanceItemDTO> list = new ArrayList<>(instanceList.size());
        /*无心跳或心跳超时（默认70s."连续2次采集无心跳响应则离线"）为离线,有心跳且状态不是警告/严重时，正常*/
        LocalDateTime offlineBefore = LocalDateTime.now().minusSeconds(offlineThresholdSeconds);
        for (RedisInstance row : instanceList) {
            RedisInstanceItemDTO item = new RedisInstanceItemDTO();
            item.setInstanceId(row.getInstanceId());
            item.setInstanceName(row.getInstanceName());
            item.setHostIp(row.getHostIp());
            item.setRedisPort(row.getRedisPort());
            item.setRole(row.getRole());
            item.setRedisVersion(row.getRedisVersion());
            item.setLastHeartbeatTime(row.getLastHeartbeatTime());
            item.setMasterHostIp(row.getMasterHostIp());
            item.setMasterRedisPort(row.getMasterRedisPort());

            //无心跳、心跳超时，离线；有心跳，但不是告警/严重，正常；其余情况保留原状态
            if (row.getLastHeartbeatTime() == null || row.getLastHeartbeatTime().isBefore(offlineBefore)) {
                item.setRunStatus(RunStatusEnum.RUN_OFFLINE.getDesc());
            }
            else if (!RunStatusEnum.RUN_CRITICAL.getDesc().equals(row.getRunStatus())
                    && !RunStatusEnum.RUN_WARNING.getDesc().equals(row.getRunStatus())) {
                item.setRunStatus(RunStatusEnum.RUN_NORMAL.getDesc());
            }
            else{
                item.setRunStatus(row.getRunStatus());
            }

            list.add(item);

        }
        caffeineCache.put(key, list);
        return list;
    }

    /**
     * 查询核心实时指标
     * @param instanceId
     * @return
     */
    @Override
    public MetricCurrentValueResponse getCurrentValues(String instanceId) {
        RedisInstance inst = redisInstanceService.getRedisInstance(instanceId);

        if (inst == null) {
            throw new BusinessException(BusinessCode.NOT_FOUND_ERROR.getCode(), "实例不存在");
        }

        String key = CacheKeyUtil.realtimeCurrent(instanceId);
        Object cached = caffeineCache.getIfPresent(key);
        if (cached != null) {
            return (MetricCurrentValueResponse) cached;
        }

        //取最新2条记录
        List<MetricLog> lastTwo = metricLogMapper.selectList(
                new LambdaQueryWrapper<MetricLog>()
                        .eq(MetricLog::getInstanceId, instanceId)
                        .orderByDesc(MetricLog::getTm)
                        .orderByDesc(MetricLog::getId)   // 同tm 时定序，保证 latest/prev 相邻
                        .last("LIMIT 2")
        );

        MetricLog latest = lastTwo.size() > 0 ? lastTwo.get(0) : null;
        MetricLog prev   = lastTwo.size() > 1 ? lastTwo.get(1) : null;

        // 查询启用中的、适用于当前实例的告警规则
        List<AlarmRule> boundRules = alarmRuleMapper.selectList(new LambdaQueryWrapper<AlarmRule>()
                .eq(AlarmRule::getEnableStatus, CommonConstant.ENABLE)
                .and(w -> w.eq(AlarmRule::getScopeType, CommonConstant.ALL_INSTANCE_IDS)
                        .or()
                        .like(AlarmRule::getScopeInstanceId, instanceId)
                )
        );
        Map<String, String> latestMap = toMetricMap(latest);
        Map<String, String> prevMap   = toMetricMap(prev);

        List<MetricCurrentValueDTO> metrics = new ArrayList<>();

        //只遍历核心指标
        for (MetricTypeEnum metricType : CommonConstant.CORE_METRICS) {
            MetricCurrentValueDTO m = new MetricCurrentValueDTO();
            m.setMetricKey(metricType.getKey());
            m.setMetricName(metricType.getName());
            m.setUnit(metricType.getUnit());

            // 新值：Map 取 String -> BigDecimal
            BigDecimal cur = parseMetric(latestMap.get(metricType.getKey()));
            m.setCurrentValue(cur);
            m.setMetricStatus(cur == null
                    ? AlarmLevelEnum.NO_DATA.getDesc()
                    : judgeMetricStatus(cur, metricType.getKey(), boundRules));

            // 旧值 + 环比
            BigDecimal pv = parseMetric(prevMap.get(metricType.getKey()));
            m.setChangeRate5min(calcChangeRate(cur, pv));

            metrics.add(m);
        }

        MetricCurrentValueResponse resp = new MetricCurrentValueResponse();
        resp.setMetrics(metrics);
        caffeineCache.put(key, resp);
        return resp;
    }

    /** 把采集记录按指标 key 转成 Map；log 为 null 返回空 Map，避免外层判空 */
    private static Map<String, String> toMetricMap(MetricLog metricLog) {
        Map<String, String> map = new HashMap<>(16);
        if (metricLog == null) {
            return map;
        }
        map.put(MetricTypeEnum.CPU_USAGE.getKey(),            metricLog.getCpuPer());
        map.put(MetricTypeEnum.SERVER_MEM_USAGE.getKey(),     metricLog.getMemServerPer());
        map.put(MetricTypeEnum.REDIS_MEM_USED.getKey(),       metricLog.getMemUs());
        map.put(MetricTypeEnum.QPS.getKey(),                  metricLog.getOps());
        map.put(MetricTypeEnum.AVG_LATENCY.getKey(),          metricLog.getLatency());
        map.put(MetricTypeEnum.NET_INPUT_BANDWIDTH.getKey(),  metricLog.getIoIik());
        map.put(MetricTypeEnum.NET_OUTPUT_BANDWIDTH.getKey(), metricLog.getIoIok());
        return map;
    }

    /** null/空串/非数字 → null（视为无数据） */
    private static BigDecimal parseMetric(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.isEmpty()) return null;
        try {
            return new BigDecimal(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** (cur - prev) / prev * 100，保留 2 位；任一无值或 prev 为 0 → null */
    private static BigDecimal calcChangeRate(BigDecimal cur, BigDecimal prev) {
        if (cur == null || prev == null || prev.signum() == 0) return null;
        return cur.subtract(prev)
                .divide(prev, 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
    }


    /**
     * 根据告警规则判断指标状态，返回指标中文描述
     * @param currentVal
     * @param metricKey
     * @param rules
     * @return
     */
    private String judgeMetricStatus(BigDecimal currentVal, String metricKey, List<AlarmRule> rules) {
        double value;
        try {
            value = currentVal.doubleValue();
        } catch (Exception e) {
            return AlarmLevelEnum.NORMAL.getDesc();
        }

        Integer maxLevel = ParseUtils.stringToInteger(AlarmLevelEnum.NORMAL.getCode());

        for (AlarmRule rule : rules) {
            if (!metricKey.equals(rule.getMetricKey()) || rule.getThreshold() == null) {
                continue;
            }

            double threshold;
            try {
                threshold = Double.parseDouble(rule.getThreshold());
            } catch (Exception e) {
                continue;
            }
            String compareType = rule.getCompareType();
            boolean triggered = false;
            triggered=CompareUtil.compareValue(value,compareType,threshold);

            if (triggered && rule.getAlarmLevel() != null) {
                Integer level = ParseUtils.stringToInteger(AlarmLevelEnum.codeOf((rule.getAlarmLevel())));
                if (level > maxLevel) {
                    maxLevel = level;
                }
            }
        }

        return AlarmLevelEnum.descOf(String.valueOf(maxLevel));
    }

    /**
     * 多节点指标趋势
     */
    @Override
    public TrendResponse getTrend(TrendRequest req) {
        // 缓存 key：不再含 metricKey（一次查询已覆盖所有指标）
        String key = CacheKeyUtil.realtimeTrend(req.getInstanceIds(), req.getTimeRange());
        Object cached = caffeineCache.getIfPresent(key);
        if (cached != null) {
            return (TrendResponse) cached;
        }

        boolean near1h = CommonConstant.NEAR_1HOUR.equals(req.getTimeRange());
        LocalDateTime endTime = LocalDateTime.now();
        LocalDateTime startTime = near1h ? endTime.minusHours(1) : endTime.minusHours(6);
        int bucketMinutes = near1h ? 5 : 15;

        // 一次查询：返回所有实例 × 所有指标列
        List<Map<String, Object>> rows = redisInstanceMapper.selectTrendData(
                req.getInstanceIds(), bucketMinutes,
                startTime.format(FORMATTER), endTime.format(FORMATTER));

        // 统一时间轴
        List<String> timestampList = buildAlignedTimestamps(startTime, endTime, bucketMinutes);

        // 按实例分组（每行含 bucket + 全部指标列）
        Map<String, List<Map<String, Object>>> grouped = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String iid = String.valueOf(row.get("instanceId"));
            grouped.computeIfAbsent(iid, k -> new ArrayList<>()).add(row);
        }

        // 每个核心指标构建一个 TrendMetricDTO
        List<TrendMetricDTO> metricList = new ArrayList<>(CommonConstant.CORE_METRICS.size());
        for (MetricTypeEnum mt : CommonConstant.CORE_METRICS) {
            TrendMetricDTO md = new TrendMetricDTO();
            md.setMetricKey(mt.getKey());
            md.setMetricName(mt.getName());
            md.setUnit(mt.getUnit());

            List<TrendSeriesDTO> seriesList = new ArrayList<>(grouped.size());
            for (Map.Entry<String, List<Map<String, Object>>> entry : grouped.entrySet()) {
                // 从该实例的行中，取 mt.getKey() 这一列，按 timestampList 对齐
                seriesList.add(buildAlignedSeries(
                        entry.getKey(), entry.getValue(), timestampList, mt.getKey()));
            }
            md.setSeriesList(seriesList);
            metricList.add(md);
        }

        TrendResponse resp = new TrendResponse();
        resp.setTimestampList(timestampList);
        resp.setMetricList(metricList);
        caffeineCache.put(key, resp);
        return resp;
    }

    /**
     * 生成对齐后的统一时间轴（yyyy-MM-dd HH:mm:ss，升序）。
     */
    private List<String> buildAlignedTimestamps(LocalDateTime start, LocalDateTime end, int bucketMinutes) {
        List<String> timestamps = new ArrayList<>();
        LocalDateTime cursor = truncateToBucket(start, bucketMinutes);
        while (!cursor.isAfter(end)) {
            timestamps.add(cursor.format(FORMATTER));
            cursor = cursor.plusMinutes(bucketMinutes);
        }
        return timestamps;
    }

    /**
     * 将时间按分桶粒度向下取整（与 SQL bucketExpr 口径一致）。
     */
    private LocalDateTime truncateToBucket(LocalDateTime time, int bucketMinutes) {
        int minuteOfHour = time.getMinute();
        int aligned = (minuteOfHour / bucketMinutes) * bucketMinutes;
        return time.withMinute(aligned).withSecond(0).withNano(0);
    }


    /**
     *构建按统一时间轴对齐的趋势序列
     * @param instanceId
     * @param rows
     * @param timestampList
     * @param valueColumn
     * @return
     */
    private TrendSeriesDTO buildAlignedSeries(String instanceId,
                                              List<Map<String, Object>> rows,
                                              List<String> timestampList,
                                              String valueColumn) {
        // bucket(格式化后) -> 该指标值
        Map<String, Object> bucketToValue = new HashMap<>(rows.size() * 2);
        for (Map<String, Object> row : rows) {
            Object bucket = row.get("bucket");
            if (bucket == null) continue;
            bucketToValue.put(formatBucket(bucket), row.get(valueColumn));
        }

        // 按统一时间轴对齐，缺失补 null
        List<Map<String, Object>> points = new ArrayList<>(timestampList.size());
        for (String ts : timestampList) {
            Map<String, Object> p = new LinkedHashMap<>(2);
            p.put("bucket", ts);
            p.put("value", bucketToValue.get(ts));
            points.add(p);
        }

        TrendSeriesDTO s = new TrendSeriesDTO();
        s.setInstanceId(instanceId);
        s.setPoints(points);
        return s;
    }

    /**
     * 统一时间桶格式：JDBC 可能返回 Timestamp/LocalDateTime/String，统一为 yyyy-MM-dd HH:mm:ss。
     */
    private String formatBucket(Object bucket) {
        if (bucket == null) {
            return null;
        }
        if (bucket instanceof LocalDateTime) {
            return ((LocalDateTime) bucket).format(FORMATTER);
        }
        if (bucket instanceof java.sql.Timestamp) {
            return ((java.sql.Timestamp) bucket).toLocalDateTime().format(FORMATTER);
        }
        if (bucket instanceof Date) {
            return new java.sql.Timestamp(((Date) bucket).getTime()).toLocalDateTime().format(FORMATTER);
        }
        String text = bucket.toString();
        // 兼容 "yyyy-MM-dd HH:mm:ss.0" 形式
        if (text.length() > 19) {
            text = text.substring(0, 19);
        }
        return text;
    }



    /**
     * 主从拓扑。
     */
    @Override
    public TopologyResponse getTopology() {
        Object cached = caffeineCache.getIfPresent(CacheKeyUtil.TOPOLOGY);
        if (cached != null) {
            return (TopologyResponse) cached;
        }

        List<RedisInstance> instances = selectActiveInstances(CommonConstant.MANAGE_ACTIVE);

        List<AlarmRule> allRules = alarmRuleMapper.selectList(Wrappers.<AlarmRule>lambdaQuery()
                .eq(AlarmRule::getEnableStatus, CommonConstant.ENABLE)
        );

        // instanceName 本身就是 host:port，直接用它做映射，不再手工拼 hostIp:redisPort
        Map<String, RedisInstance> nameToInstance = new HashMap<>(instances.size() * 2);
        for (RedisInstance inst : instances) {
            if (inst.getInstanceName() != null) {
                nameToInstance.put(inst.getInstanceName(), inst);
            }
        }

        List<TopologyNodeDTO> nodes = new ArrayList<>();
        boolean masterOnline = false;
        boolean anyReplicaDelay = false;

        for (RedisInstance inst : instances) {
            TopologyNodeDTO n = new TopologyNodeDTO();
            n.setInstanceId(inst.getInstanceId());
            n.setInstanceName(inst.getInstanceName());
            n.setIp(inst.getHostIp());
            n.setPort(inst.getRedisPort());
            n.setRole(inst.getRole());

            // 主节点 host:port，与 instanceName 同一格式，直接反查
            String masterHostPort = (inst.getMasterHostIp() != null ? inst.getMasterHostIp() : "null")
                    + ":" + (inst.getMasterRedisPort() != null ? inst.getMasterRedisPort() : "null");
            RedisInstance master = nameToInstance.get(masterHostPort);
            n.setMasterName(master != null ? master.getInstanceId() : masterHostPort);

            // 从 rms_metric_log 最新记录获取复制延迟
            String replDelay = getLatestReplDelay(inst.getInstanceId());
            n.setCopyDelay(replDelay != null ? replDelay : "0");

            // 根据告警规则判断复制状态
            String copyStatus = judgeReplDelayStatus(inst.getInstanceId(), replDelay, allRules);
            n.setCopyStatus(copyStatus);

            boolean isMaster = "MASTER".equalsIgnoreCase(inst.getRole());
            if (isMaster) {
                masterOnline = isOnline(inst);
            } else if ("延迟".equals(copyStatus)) {
                anyReplicaDelay = true;
            }
            nodes.add(n);
        }

        String topologyStatus;
        if (masterOnline && !anyReplicaDelay) {
            topologyStatus = CommonConstant.TOPOLOGY_HEALTH;
        } else if (masterOnline && anyReplicaDelay) {
            topologyStatus = CommonConstant.TOPOLOGY_WARNING;
        } else {
            topologyStatus = CommonConstant.TOPOLOGY_ANOMALY;
        }

        // 最近一次切换事件
        RedisSwitchEvent lastEvent = redisSwitchEventService.selectLatestSwitchEvent();

        TopologyResponse resp = new TopologyResponse();
        resp.setNodes(nodes);
        resp.setTopologyStatus(topologyStatus);
        resp.setLastSwitch(lastEvent);

        caffeineCache.put(CacheKeyUtil.TOPOLOGY, resp);
        return resp;
    }

    /**
     * 主从状态表格。
     */
    @Override
    public List<NodeStatusResponse> getNodesStatus() {
        Object cached = caffeineCache.getIfPresent(CacheKeyUtil.NODES);
        if (cached != null) {
            @SuppressWarnings("unchecked")
            List<NodeStatusResponse> ret = (List<NodeStatusResponse>) cached;
            return ret;
        }

        //询纳管中的实例，按名称排序,用于表格
        List<RedisInstance> instances = redisInstanceMapper.selectList(new LambdaQueryWrapper<RedisInstance>()
                .eq(RedisInstance::getManagementStatus,CommonConstant.MANAGE_ACTIVE)
                .orderByAsc(RedisInstance::getInstanceName)
        );

        //所有启用的规则
        List<AlarmRule> allRules= alarmRuleMapper.selectList(Wrappers.<AlarmRule>lambdaQuery()
                .eq(AlarmRule::getEnableStatus, CommonConstant.ENABLE)
        );


        List<NodeStatusResponse> masterRows = new ArrayList<>();
        List<NodeStatusResponse> slaveRows = new ArrayList<>();


        String masterIp = null;
        String masterPort = null;
        for (RedisInstance i : instances) {
            NodeStatusResponse m = new NodeStatusResponse();
            m.setInstanceId(i.getInstanceId());
            m.setInstanceName(i.getInstanceName());
            m.setIp(i.getHostIp());
            m.setPort(i.getRedisPort());
            m.setRole(i.getRole());

            // masterName 拼接为 host:port
            masterIp = i.getMasterHostIp().equals("") ? "null" : i.getMasterHostIp();
            masterPort = i.getMasterRedisPort().equals("") ? "null" : i.getMasterRedisPort();
            m.setMasterName(masterIp + ":" + masterPort);

            //复制延迟从 rms_metric_log 最新记录获取
            String replDelay = getLatestReplDelay(i.getInstanceId());
            m.setCopyDelay(!replDelay.equals("") ? replDelay : "0");
            //复制状态由告警规则判断
            m.setCopyStatus(judgeReplDelayStatus(i.getInstanceId(), replDelay, allRules));
            m.setRunStatus(RunStatusEnum.descOf(i.getRunStatus()));
            if (CommonConstant.DEFAULT_ROLE_MASTER.equalsIgnoreCase(i.getRole())) {
                masterRows.add(m);
            } else {
                slaveRows.add(m);
            }

        }
        //从节点按实例名升序
        slaveRows.sort(Comparator.comparing(NodeStatusResponse::getInstanceName,
                Comparator.nullsLast(String::compareTo)));

        List<NodeStatusResponse> result = new ArrayList<>(masterRows.size() + slaveRows.size());
        result.addAll(masterRows);
        result.addAll(slaveRows);

        caffeineCache.put(CacheKeyUtil.NODES, result);
        return result;
    }

    /**
     * 日周报查询。
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
     * 组装完整报告数据，查询与导出共用。
     */
    @Override
    public ReportQueryResponse buildReportData(String instanceId, String reportType) {
        boolean daily = !CommonConstant.REPORT_TYPE_WEEK.equals(reportType);
        LocalDateTime endTime = LocalDateTime.now();
        // 日报：00:00 至当前时刻； 周报：当前日期向前推6天到当天共7自然日
        LocalDateTime startTime = daily
                ? endTime.toLocalDate().atStartOfDay()
                : endTime.toLocalDate().minusDays(6).atStartOfDay();
        String startText = startTime.format(FORMATTER);
        String endText = endTime.format(FORMATTER);

        ReportQueryResponse resp = new ReportQueryResponse();

        // 概览统计
        ReportOverviewStatsDTO stats = redisInstanceMapper.selectReportOverviewStats(
                instanceId, startText, endText);

        ReportOverviewDTO ov = new ReportOverviewDTO();
        ov.setCpuAvg(stats.getCpuAvg().toString());
        ov.setCpuMax(stats.getCpuMax().toString());
        ov.setCpuMaxTim(stats.getCpuMaxTim());
        ov.setRedisMemAvg(stats.getRedisMemAvg().toString());
        ov.setRedisMemMax(stats.getRedisMemMax().toString());
        ov.setRedisMemMaxTim(stats.getRedisMemMaxTim());
        ov.setQpsAvg(stats.getQpsAvg().toString());
        ov.setQpsMax(stats.getQpsMax().toString());
        ov.setQpsMaxTim(stats.getQpsMaxTim());
        ov.setLatencyAvg(stats.getLatencyAvg().toString());
        ov.setLatencyMax(stats.getLatencyMax().toString());
        ov.setLatencyMaxTim(stats.getLatencyMaxTim());

        // 告警统计（按时间范围，可按实例过滤）
        AlarmStatsDTO alarmStats = alarmRecordMapper.selectReportAlarmStats(
                instanceId, startText, endText);

        if (alarmStats == null) {
            alarmStats = new AlarmStatsDTO();
            alarmStats.setAlarmTotal(0L);
            alarmStats.setInfoCount(0L);
            alarmStats.setAlarmCount(0L);
            alarmStats.setSeverityCount(0L);

        }
        ov.setAlarmStatsDTO(alarmStats);


        // 主从切换统计
        SwitchStatsDTO switchStats = switchEventMapper.selectSwitchStats(startText, endText);

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
        List<RedisInstance> allInstances = selectActiveInstances(CommonConstant.MANAGE_ACTIVE);
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
        List<Map<String, Object>> trendRows = redisInstanceMapper.selectReportTrend(
                instanceId, startText, endText, bucketMinutes, truncateDay);
        resp.setTrendList(buildReportTrends(trendRows, startTime, endTime, bucketMinutes));

        // 告警与切换事件合并列表
        List<Map<String, Object>> alarmRows = alarmRecordMapper.selectReportEventList(
                instanceId, startText, endText, 500);

        List<Map<String, Object>> switchRows = switchEventMapper.selectSwitchEventList(
                startText, endText, 500);
        resp.setEventList(buildReportEvents(alarmRows, switchRows));

        return resp;
    }

    /**
     * 组装4项指标趋势序列，时间轴对齐、缺失补 null。
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
                {"avgLatency", "平均延迟", "ms"}
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
     * 合并告警与主从切换明细，按触发时间倒序，补充节点展示字段。
     */
    private List<ReportEventDTO> buildReportEvents(List<Map<String, Object>> alarmRows,
                                                   List<Map<String, Object>> switchRows) {
        List<ReportEventDTO> events = new ArrayList<>();
        if (alarmRows != null) {
            for (Map<String, Object> row : alarmRows) {
                ReportEventDTO event = new ReportEventDTO();
                event.setEventType(TypeEnum.ALARM.getDesc());
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
                event.setEventType(TypeEnum.MASTER_SWITCH.getDesc());
                event.setNodeDisplay(row.get("oldInstanceName") + " → " + row.get("newInstanceName"));
                event.setContent(event.getEventType()+"：" + event.getNodeDisplay()
                        + "（" + TypeEnum.descOf(str(row.get("switchType"))) + "）");
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
        if (value instanceof java.sql.Timestamp) {
            return ((java.sql.Timestamp) value).toLocalDateTime();
        }
        if (value instanceof Date) {
            return new java.sql.Timestamp(((Date) value).getTime()).toLocalDateTime();
        }
        try {
            return LocalDateTime.parse(value.toString().replace(" ", "T"));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 报告导出。
     * 补充 exportType 入参，异步生成文件并更新任务状态，
     * 任务与文件30分钟有效；原实现为占位空任务。
     */
    @Override
    public ReportExportResponse exportReport(String instanceId, String reportType) {
        String taskId = "EP_" + IdUtil.getSnowflakeNextIdStr();;
        ReportExportTaskHolder.ExportTask task = taskHolder.createTask(taskId);
        // 异步执行（AsyncTaskRunner 为独立 Bean，保证 @Async 生效）
        asyncTaskRunner.run(() -> doExport(taskId, instanceId, reportType));

        ReportExportResponse resp = new ReportExportResponse();
        resp.setTaskId(taskId);
        resp.setTaskStatus(task.getTaskStatus());
        return resp;
    }

    /**
     * 报告导出异步任务执行体：生成文件并更新任务状态
     */
    private void doExport(String taskId, String instanceId, String reportType) {
        ReportExportTaskHolder.ExportTask task = taskHolder.getTask(taskId);
        if (task == null) {
            return;
        }
        try {
            log.info("[报告导出] 异步任务开始, taskId={}, instanceId={}, reportType={}",
                    taskId, instanceId, reportType);

            // 复用报告查询逻辑获取完整报告数据
            ReportQueryResponse report = buildReportData(instanceId, reportType);
            File file = reportFileGenerator.generate(report, instanceId, reportType,
                    taskHolder.exportDir());

            task.setFilePath(file.getAbsolutePath());
            task.setFileName(file.getName());
            task.setDownloadUrl("/monitor/report/export/download?taskId=" + taskId);
            task.setTaskStatus("success");

            /* 将操作人、文件id、报告类型、导出实例、时间范围、操作类型、操作时间记入操作审计日志 */
            auditLogService.record("报告导出", "导出", taskId,
                    "导出Redis运行报告，实例：" + instanceId + "，报告类型：" + reportType);

            log.info("[报告导出] 异步任务完成, taskId={}, file={}", taskId, file.getAbsolutePath());
        } catch (Exception e) {
            task.setTaskStatus("fail");
            task.setFailReason(e.getMessage());
            log.error("[报告导出] 异步任务失败, taskId={}", taskId, e);
        }
    }

    /**
     * 查询导出任务状态。
     */
    @Override
    public ReportExportResponse queryExportTask(String taskId) {
        ReportExportTaskHolder.ExportTask task = taskHolder.getTask(taskId);
        if (task == null) {
            throw new BusinessException(BusinessCode.NOT_FOUND_ERROR.getCode(), "导出任务不存在或已过期");
        }
        ReportExportResponse resp = new ReportExportResponse();
        resp.setTaskId(task.getTaskId());
        resp.setTaskStatus(task.getTaskStatus());
        resp.setDownloadUrl(task.getDownloadUrl());
        resp.setFailReason(task.getFailReason());
        return resp;
    }

    /**
     * 获取导出文件。
     */
    @Override
    public File getExportFile(String taskId) {
        ReportExportTaskHolder.ExportTask task = taskHolder.getTask(taskId);
        if (task == null) {
            throw new BusinessException(BusinessCode.NOT_FOUND_ERROR.getCode(), "导出任务不存在或已过期");
        }
        if (!"success".equals(task.getTaskStatus()) || task.getFilePath() == null) {
            throw new BusinessException(BusinessCode.OPERATION_ERROR.getCode(), "导出任务未完成，无法下载");
        }
        File file = new File(task.getFilePath());
        if (!file.exists()) {
            throw new BusinessException(BusinessCode.OPERATION_ERROR.getCode(), "导出文件已过期，请重新导出");
        }
        return file;
    }

    /**
     * 取实例最新复制延迟（从 rms_metric_log 最新记录）。
     */
    private String getLatestReplDelay(String instanceId) {
        MetricLog latest = metricLogMapper.selectLatestByInstanceId(instanceId);
        return latest == null ? null : latest.getReplDelay();
    }

    /**
     * 根据告警规则判断复制状态。
     * - 无规则限制      -> 正常
     * - 有规则但未触发  -> 正常
     * - 有规则且触发    -> 延迟
     */
    private String judgeReplDelayStatus(String instanceId, String replDelay, List<AlarmRule> rules) {
        if (replDelay == null || replDelay.trim().isEmpty()) {
            return CommonConstant.NORMAL;
        }
        double value;
        try {
            value = Double.parseDouble(replDelay.trim());
        } catch (Exception e) {
            return CommonConstant.NORMAL;
        }

        //判断是否有规则被触发
        for (AlarmRule rule : rules) {
            if (!REPL_DELAY_METRIC_KEY.equals(rule.getMetricKey())) {
                continue;
            }
            if (!ruleMatchHelper.isRuleApplyToInstance(rule, instanceId)) {
                continue;
            }
            if (rule.getThreshold() == null) {
                continue;
            }
            double threshold;
            try {
                threshold = Double.parseDouble(rule.getThreshold());
            } catch (Exception e) {
                continue;
            }
            if (CompareUtil.compareValue(value, rule.getCompareType(), threshold)) {
                return CommonConstant.DELAY;
            }
        }
        // 无规则 / 规则未触发 → 正常
        return CommonConstant.NORMAL;
    }



    /**
     * 判断实例是否在线：有心跳且不是"离线"
     */
    private boolean isOnline(RedisInstance inst) {
        if (inst.getLastHeartbeatTime() == null) {
            return false;
        }
        String desc = RunStatusEnum.descOf(inst.getRunStatus());
        return !RunStatusEnum.RUN_OFFLINE.getDesc().equals(desc);
    }

    /**
     * 更新实例表，心跳、运行状态
     * @param instanceId
     * @param now
     * @param runStatus
     * @return
     */
    public Boolean updateHeartbeatAndStatus(String instanceId, LocalDateTime now, String runStatus) {
        int rows=redisInstanceMapper.update(null,new LambdaUpdateWrapper<RedisInstance>()
                .set(RedisInstance::getLastHeartbeatTime,now)
                .set(RedisInstance::getRunStatus,runStatus)
                .set(RedisInstance::getUpdatedTime,now)
                .eq(RedisInstance::getInstanceId,instanceId)
        );
        if(rows>0) {
            log.info("执行器上报实例心跳、运行状态更新:{}",CommonConstant.SUCCESS);
            return true;
        }
        else {
            log.info("执行器上报实例心跳、运行状态更新:{}",CommonConstant.FAILURE);
            return false;
        }
    }

    public RedisInstance getRedisInstance(String instanceId) {
        RedisInstance instance = redisInstanceMapper.selectOne(Wrappers.<RedisInstance>lambdaQuery()
                .eq(RedisInstance::getInstanceId,instanceId));
        return instance;
    }



    /** 查询纳管中的实例,用于拓扑 */
    public List<RedisInstance> selectActiveInstances(String managementStatus) {
        List<RedisInstance> instances = redisInstanceMapper.selectList(new LambdaQueryWrapper<RedisInstance>()
                .eq(RedisInstance::getManagementStatus,managementStatus)
        );
        return instances;
    }


}