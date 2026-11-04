package com.danuvius.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.danuvius.platform.common.constant.CommonConstant;
import com.danuvius.platform.mapper.AlarmRuleMapper;
import com.danuvius.platform.mapper.MetricLogMapper;
import com.danuvius.platform.mapper.RedisInstanceMapper;
import com.danuvius.platform.model.dto.monitor.NodeStatusResponse;
import com.danuvius.platform.model.dto.monitor.TopologyNodeDTO;
import com.danuvius.platform.model.dto.monitor.TopologyResponse;
import com.danuvius.platform.model.entity.AlarmRule;
import com.danuvius.platform.model.entity.MetricLog;
import com.danuvius.platform.model.entity.RedisInstance;
import com.danuvius.platform.model.entity.RedisSwitchEvent;
import com.danuvius.platform.model.enums.instance.ManagementStatusEnum;
import com.danuvius.platform.model.enums.instance.RunStatusEnum;
import com.danuvius.platform.model.enums.metric.MetricTypeEnum;
import com.danuvius.platform.model.enums.rule.EnableStatusEnum;
import com.danuvius.platform.service.RedisSwitchEventService;
import com.danuvius.platform.service.TopologyQueryService;
import com.danuvius.platform.util.CacheKeyUtil;
import com.danuvius.platform.util.CompareUtil;
import com.danuvius.platform.util.RuleMatchHelper;
import com.github.benmanes.caffeine.cache.Cache;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;

/**
 * 主从拓扑与节点状态查询实现。
 *
 * <p>仅负责拓扑、节点表格的组装与缓存，不承担实例维护职责。</p>
 */
@Service
public class TopologyQueryServiceImpl implements TopologyQueryService {

    private final RedisInstanceMapper redisInstanceMapper;
    private final AlarmRuleMapper alarmRuleMapper;
    private final MetricLogMapper metricLogMapper;
    private final RedisSwitchEventService redisSwitchEventService;
    private final RuleMatchHelper ruleMatchHelper;
    private final Cache<String, Object> caffeineCache;

    public TopologyQueryServiceImpl(RedisInstanceMapper redisInstanceMapper,
                                    AlarmRuleMapper alarmRuleMapper,
                                    MetricLogMapper metricLogMapper,
                                    RedisSwitchEventService redisSwitchEventService,
                                    RuleMatchHelper ruleMatchHelper,
                                    Cache<String, Object> caffeineCache) {
        this.redisInstanceMapper = redisInstanceMapper;
        this.alarmRuleMapper = alarmRuleMapper;
        this.metricLogMapper = metricLogMapper;
        this.redisSwitchEventService = redisSwitchEventService;
        this.ruleMatchHelper = ruleMatchHelper;
        this.caffeineCache = caffeineCache;
    }

    /**
     * 主从拓扑
     */
    @Override
    public TopologyResponse getTopology() {
        Object cached = caffeineCache.getIfPresent(CacheKeyUtil.TOPOLOGY);
        if (cached != null) {
            return (TopologyResponse) cached;
        }

        List<RedisInstance> instances = selectActiveInstances(ManagementStatusEnum.ACTIVE.getCode());

        List<AlarmRule> allRules = alarmRuleMapper.selectList(Wrappers.<AlarmRule>lambdaQuery()
                .eq(AlarmRule::getEnableStatus, EnableStatusEnum.ENABLE.getCode())
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

            boolean isMaster = CommonConstant.MASTER.equalsIgnoreCase(inst.getRole());
            if (isMaster) {
                masterOnline = isOnline(inst);
            } else if (CommonConstant.DELAY.equals(copyStatus)) {
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
     * 主从状态表格
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
                .eq(RedisInstance::getManagementStatus, ManagementStatusEnum.ACTIVE.getCode())
                .orderByAsc(RedisInstance::getInstanceName)
        );

        //所有启用的规则
        List<AlarmRule> allRules = alarmRuleMapper.selectList(Wrappers.<AlarmRule>lambdaQuery()
                .eq(AlarmRule::getEnableStatus, EnableStatusEnum.ENABLE.getCode())
        );

        List<NodeStatusResponse> masterRows = new ArrayList<>();
        List<NodeStatusResponse> slaveRows = new ArrayList<>();

        String masterIp;
        String masterPort;
        for (RedisInstance i : instances) {
            NodeStatusResponse m = new NodeStatusResponse();
            m.setInstanceId(i.getInstanceId());
            m.setInstanceName(i.getInstanceName());
            m.setIp(i.getHostIp());
            m.setPort(i.getRedisPort());
            m.setRole(i.getRole());

            // masterName 拼接为 host:port
            masterIp = StringUtils.hasText(i.getMasterHostIp()) ? i.getMasterHostIp() : "null";
            masterPort = StringUtils.hasText(i.getMasterRedisPort()) ? i.getMasterRedisPort() : "null";
            m.setMasterName(masterIp + ":" + masterPort);

            //复制延迟、连通延迟统一从 rms_metric_log 最新记录获取
            MetricLog latestMetric = metricLogMapper.selectLatestByInstanceId(i.getInstanceId());
            String replDelay = latestMetric == null ? null : latestMetric.getReplDelay();
            String safeReplDelay = replDelay == null ? "" : replDelay.trim();
            m.setCopyDelay(!safeReplDelay.isEmpty() ? safeReplDelay : "0");
            //复制状态由告警规则判断
            m.setCopyStatus(judgeReplDelayStatus(i.getInstanceId(), replDelay, allRules));

            // 最近一次采集中的“执行器 ping 平台”延迟
            m.setPingLatency(latestMetric == null ? null : latestMetric.getPingLatency());

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

    /** 查询纳管中的实例 */
    private List<RedisInstance> selectActiveInstances(String managementStatus) {
        return redisInstanceMapper.selectList(new LambdaQueryWrapper<RedisInstance>()
                .eq(RedisInstance::getManagementStatus, managementStatus)
        );
    }

    /**
     * 取实例最新复制延迟
     */
    private String getLatestReplDelay(String instanceId) {
        MetricLog latest = metricLogMapper.selectLatestByInstanceId(instanceId);
        return latest == null ? null : latest.getReplDelay();
    }

    /**
     * 根据告警规则判断复制状态
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
            if (!MetricTypeEnum.REPL_DELAY.getKey().equals(rule.getMetricKey())) {
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
}
