package com.danuvius.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.danuvius.platform.common.constant.CommonConstant;
import com.danuvius.platform.common.exception.BusinessCode;
import com.danuvius.platform.common.exception.BusinessException;
import com.danuvius.platform.mapper.AlarmRuleMapper;
import com.danuvius.platform.mapper.MetricLogMapper;
import com.danuvius.platform.mapper.RedisInstanceMapper;
import com.danuvius.platform.model.dto.metric.MetricCurrentValueDTO;
import com.danuvius.platform.model.dto.monitor.*;
import com.danuvius.platform.model.entity.AlarmRule;
import com.danuvius.platform.model.entity.MetricLog;
import com.danuvius.platform.model.entity.RedisInstance;
import com.danuvius.platform.model.enums.instance.RunStatusEnum;
import com.danuvius.platform.model.enums.metric.CollectTypeEnum;
import com.danuvius.platform.model.enums.metric.MetricTypeEnum;
import com.danuvius.platform.model.enums.rule.AlarmLevelEnum;
import com.danuvius.platform.model.enums.rule.EnableStatusEnum;
import com.danuvius.platform.model.enums.rule.ScopeTypeEnum;
import com.danuvius.platform.service.RealtimeMetricQueryService;
import com.danuvius.platform.util.CacheKeyUtil;
import com.danuvius.platform.util.CompareUtil;
import com.danuvius.platform.util.ParseUtils;
import com.danuvius.platform.util.TimeBucketUtil;
import com.github.benmanes.caffeine.cache.Cache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 实时监控查询实现：实例列表、核心实时指标与多节点趋势
 */
@Slf4j
@Service
public class RealtimeMetricQueryServiceImpl implements RealtimeMetricQueryService {

    /* 实例离线判定阈值：超过该时长无心跳视为离线，平台端+执行器架构下按心跳时长判定，默认600s，可配置） */
    @Value("${platform.instance.offline-threshold-seconds:600}")
    private long offlineThresholdSeconds;

    private final RedisInstanceMapper redisInstanceMapper;
    private final AlarmRuleMapper alarmRuleMapper;
    private final MetricLogMapper metricLogMapper;
    private final Cache<String, Object> caffeineCache;

    public RealtimeMetricQueryServiceImpl(RedisInstanceMapper redisInstanceMapper,
                                          AlarmRuleMapper alarmRuleMapper,
                                          MetricLogMapper metricLogMapper,
                                          Cache<String, Object> caffeineCache) {
        this.redisInstanceMapper = redisInstanceMapper;
        this.alarmRuleMapper = alarmRuleMapper;
        this.metricLogMapper = metricLogMapper;
        this.caffeineCache = caffeineCache;
    }

    /**
     * 查询实例列表
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
     * @return  核心实时指标列表（6 项）数据
     */
    @Override
    public MetricCurrentValueResponse getCurrentValues(String instanceId) {
        RedisInstance inst = getRedisInstance(instanceId);

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
                .eq(AlarmRule::getEnableStatus, EnableStatusEnum.ENABLE.getCode())
                .and(w -> w.eq(AlarmRule::getScopeType, ScopeTypeEnum.ALL_INSTANCE_IDS.getCode())
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
        List<Map<String, Object>> rows = metricLogMapper.selectTrendData(
                req.getInstanceIds(), bucketMinutes,
                startTime, endTime);

        // 【临时】真实数据为空时写 mock 进库，再重新查一次，走完整查询链路
//        if (rows.isEmpty()) {
//            mockTrendData(req.getInstanceIds(), startTime, endTime, bucketMinutes);
//            rows = redisInstanceMapper.selectTrendData(
//                    req.getInstanceIds(), bucketMinutes, startTime, endTime);
//            log.warn("[mock] 重新查询后行数:{}", rows.size());
//        }

        log.info("多节点指标趋势sql查询数:{},第一行:{}", rows.size(), rows.isEmpty() ? null : rows.get(0));

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
     * 生成对齐后的统一时间轴（yyyy-MM-dd HH:mm:ss，升序）
     */
    private List<String> buildAlignedTimestamps(LocalDateTime start, LocalDateTime end, int bucketMinutes) {
        return TimeBucketUtil.alignedTimestamps(start, end, bucketMinutes);
    }

    /**
     * 将时间按分桶粒度向下取整（与 SQL bucketExpr 口径一致）
     */
    private LocalDateTime truncateToBucket(LocalDateTime time, int bucketMinutes) {
        return TimeBucketUtil.truncateToBucket(time, bucketMinutes);
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
     * 统一时间桶格式：JDBC 可能返回 Timestamp/LocalDateTime/String，统一为 yyyy-MM-dd HH:mm:ss
     */
    private String formatBucket(Object bucket) {
        return TimeBucketUtil.formatBucket(bucket);
    }

    private RedisInstance getRedisInstance(String instanceId) {
        RedisInstance instance = redisInstanceMapper.selectOne(Wrappers.<RedisInstance>lambdaQuery()
                .eq(RedisInstance::getInstanceId,instanceId));
        return instance;
    }

    /**
     * 临时向 rms_metric_log 插入模拟数据，让 getTrend 能通过真实 SQL 查到
     * 幂等：每次先删除这些实例下所有 collect_type=1 的旧 mock，再按分桶插入
     */
    private void mockTrendData(List<String> instanceIds,
                               LocalDateTime startTime,
                               LocalDateTime endTime,
                               int bucketMinutes) {
        if (instanceIds == null || instanceIds.isEmpty()) {
            return;
        }

        // 清理旧 mock（不限时间范围，避免范围滑动导致数据堆积）
        int deleted = metricLogMapper.delete(Wrappers.<MetricLog>lambdaQuery()
                .in(MetricLog::getInstanceId, instanceIds)
                .eq(MetricLog::getCollectType, "1"));

        // 查实例信息，用于填充 role / masterHost / redisVersion 等字段
        List<RedisInstance> instances = redisInstanceMapper.selectList(
                Wrappers.<RedisInstance>lambdaQuery()
                        .in(RedisInstance::getInstanceId, instanceIds));
        Map<String, RedisInstance> instMap = new HashMap<>(instances.size() * 2);
        for (RedisInstance inst : instances) {
            instMap.put(inst.getInstanceId(), inst);
        }

        // 按分桶生成时间点并逐条插入
        LocalDateTime cursor = truncateToBucket(startTime, bucketMinutes);
        int inserted = 0;
        while (cursor.isBefore(endTime)) {
            // 第一个桶可能早于 startTime，用 startTime 作为 tm，保证被 "tm >= startTime" 覆盖
            LocalDateTime tm = cursor.isBefore(startTime) ? startTime : cursor;
            for (String instanceId : instanceIds) {
                MetricLog m = buildMockMetric(instanceId, tm, instMap.get(instanceId));
                if (metricLogMapper.insert(m) > 0) {
                    inserted++;
                }
            }
            cursor = cursor.plusMinutes(bucketMinutes);
        }

        log.warn("[mock] 趋势数据写入完成, instanceIds={}, 区间=[{}, {}), bucket={}min, 删除旧={}, 新增={}",
                instanceIds, startTime, endTime, bucketMinutes, deleted, inserted);
    }

    /** 构造一条模拟采集记录；同一 instanceId + tm 生成结果稳定 */
    private MetricLog buildMockMetric(String instanceId, LocalDateTime tm, RedisInstance inst) {
        long seed = Objects.hash(instanceId, tm.toString());
        Random random = new Random(seed);
        // 用分钟数做正弦相位，曲线随时间平滑波动（周期约 60 分钟）
        double phase = tm.getMinute() * Math.PI / 30.0;

        MetricLog m = new MetricLog();
        m.setInstanceId(instanceId);
        m.setTm(tm);

        // 各指标：基准值 + 正弦波动 + 随机扰动，全部转成 String（实体字段类型）
        m.setCpuPer(fmt(45    + 20 * Math.sin(phase) + (random.nextDouble() - 0.5) * 8));
        m.setMemServerPer(fmt(60 + 10 * Math.sin(phase) + (random.nextDouble() - 0.5) * 4));
        m.setMemUs(fmt(8      + 3  * Math.sin(phase) + (random.nextDouble() - 0.5) * 1.2));
        m.setLatency(fmt(2.5  + 2  * Math.sin(phase) + (random.nextDouble() - 0.5) * 0.8));
        m.setIoIik(fmt(150    + 50 * Math.sin(phase) + (random.nextDouble() - 0.5) * 20));
        m.setIoIok(fmt(120    + 40 * Math.sin(phase) + (random.nextDouble() - 0.5) * 16));
        m.setOps(fmt(2500     + 800 * Math.sin(phase) + (random.nextDouble() - 0.5) * 320));

        boolean replica = inst != null && CommonConstant.REPLICA.equalsIgnoreCase(inst.getRole());
        m.setReplDelay(replica ? fmt(Math.abs(random.nextDouble()) * 0.5) : "0");

        m.setRunId(inst != null ? inst.getInstanceName() : "mock-" + instanceId);
        m.setRole(inst != null ? inst.getRole() : CommonConstant.MASTER);
        m.setMasterHost(inst != null ? inst.getMasterHostIp() : null);
        m.setMasterPort(inst != null ? inst.getMasterRedisPort() : null);
        m.setRedisVersion(inst != null ? inst.getRedisVersion() : "7.2.4");
        m.setCollectType(CollectTypeEnum.MANUAL_COLLECT.getCode());

        LocalDateTime now = LocalDateTime.now();
        m.setCreatedTime(now);
        m.setUpdatedTime(now);
        return m;
    }

    /** 非负 + 保留 2 位小数的字符串 */
    private static String fmt(double v) {
        if (v < 0) {
            v = 0;
        }
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
