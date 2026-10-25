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
import com.danuvius.platform.model.dto.metric.MetricReportItemDTO;
import com.danuvius.platform.model.entity.*;
import com.danuvius.platform.model.enums.rule.AlarmLevelEnum;
import com.danuvius.platform.service.MetricLogService;
import com.danuvius.platform.service.RedisInstanceService;
import com.danuvius.platform.service.RedisSwitchEventService;
import com.danuvius.platform.util.CacheKeyUtil;
import com.github.benmanes.caffeine.cache.Cache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
public class MetricLogServiceImpl extends ServiceImpl<MetricLogMapper, MetricLog>
        implements MetricLogService {

    private static final String ROLE_MASTER = "MASTER";
    @Value("${platform.retain-days:7}")
    private int retainDays;

    private final MetricLogMapper metricLogMapper;
    private final NodeStatusMapper nodeStatusMapper;
    private final RedisInstanceMapper redisInstanceMapper;
    private final RedisSwitchEventMapper switchEventMapper;
    private final ExecutorInfoMapper executorInfoMapper;
    private final AlarmRecordMapper alarmRecordMapper;
    private final RedisInstanceService redisInstanceService;
    private final RedisSwitchEventService redisSwitchEventService;
    private final Cache<String, Object> caffeineCache;

    public MetricLogServiceImpl(MetricLogMapper metricLogMapper,
                                NodeStatusMapper nodeStatusMapper,
                                RedisInstanceMapper redisInstanceMapper,
                                RedisSwitchEventMapper switchEventMapper,
                                ExecutorInfoMapper executorInfoMapper,
                                AlarmRecordMapper alarmRecordMapper,
                                RedisInstanceService redisInstanceService,
                                RedisSwitchEventService redisSwitchEventService,
                                Cache<String, Object> caffeineCache) {
        this.metricLogMapper = metricLogMapper;
        this.nodeStatusMapper = nodeStatusMapper;
        this.redisInstanceMapper = redisInstanceMapper;
        this.switchEventMapper = switchEventMapper;
        this.executorInfoMapper = executorInfoMapper;
        this.alarmRecordMapper = alarmRecordMapper;
        this.redisInstanceService = redisInstanceService;
        this.redisSwitchEventService = redisSwitchEventService;
        this.caffeineCache = caffeineCache;
    }

    @Override
    public void reportMetric(MetricReportItemDTO metric) {
        ExecutorInfo executor=executorInfoMapper.selectOne(Wrappers.<ExecutorInfo>lambdaQuery()
                .eq(ExecutorInfo::getExecutorToken,metric.getToken())
        );

        if (executor == null) {
            throw new BusinessException(BusinessCode.PARAMS_ERROR.getCode(),"无效的token");
        }

        String instanceId = executor.getInstanceId();
        if (instanceId == null || instanceId.trim().isEmpty()) {
            throw new BusinessException(BusinessCode.NOT_FOUND_ERROR.getCode(),"执行器尚未完成 Redis 安装与实例绑定，" +
                    "请执行器在本地 Redis 就绪后携带 redisReady=true 与 redisInfo 再次调用 /agent/register");
        }

        // 平台内部回填
        metric.setInstanceId(instanceId);

        LocalDateTime now = LocalDateTime.now();

        // 写指标日志
        MetricLog metricLog = new MetricLog();
        metricLog.setInstanceId(metric.getInstanceId());
        metricLog.setTm(now);
        metricLog.setCpuPer(metric.getCpuPer());
        metricLog.setMemServerPer(metric.getMemServerPer());
        metricLog.setMemUs(metric.getMemUs());
        metricLog.setLatency(metric.getLatency());
        metricLog.setIoIik(metric.getIoIik());
        metricLog.setIoIok(metric.getIoIok());
        metricLog.setOps(metric.getOps());
        metricLog.setReplDelay(metric.getReplDelay());
        metricLog.setRunId(metric.getRunId());
        metricLog.setMasterHost(metric.getMasterHost());
        metricLog.setMasterPort(metric.getMasterPort());
        metricLog.setRole(metric.getRole());
        metricLog.setRedisVersion(metric.getRedisVersion());
        metricLog.setCollectType(metric.getCollectType());
        metricLog.setCreatedTime(now);
        metricLog.setUpdatedTime(now);
        metricLogMapper.insert(metricLog);

        // 采集上报成功时同步更新redis_instance 最后心跳时间与运行状态
        redisInstanceService.updateHeartbeatAndStatus(metric.getInstanceId(), now, CommonConstant.RUN_NORMAL);


        // 写节点状态日志
        NodeStatus ns = new NodeStatus();
        ns.setInstanceId(metric.getInstanceId());
        ns.setRunId(metric.getRunId());
        ns.setTm(now);
        // 能收到采集信息，默认在线
        ns.setCollectStatus(CommonConstant.ONLINE);
        // 执行器端上报的连通性延迟，没有则默认 0
        ns.setPingLatency(metric.getPingLatency());
        ns.setRole(metric.getRole());
        ns.setErrorMsg(metric.getErrorMsg());
        ns.setCreatedTime(now);
        ns.setUpdatedTime(now);
        nodeStatusMapper.insert(ns);

        // 主从切换检测
        try {
            detectMasterSwitch(metric, now);
        } catch (Exception e) {
            log.error("[主从切换检测] 异常, instanceId={}", metric.getInstanceId(), e);
        }
    }

    private void detectMasterSwitch(MetricReportItemDTO metric, LocalDateTime now) {
        String instanceId = metric.getInstanceId();
        String role = metric.getRole();
        RedisInstance inst = redisInstanceService.getRedisInstance(instanceId);
        if (inst == null) {
            return;
        }

        boolean changed = false;
        if (!role.equalsIgnoreCase(inst.getRole())) {
            changed = true;
        }
        String masterHost="";
        String masterPort="";
        if (ROLE_MASTER.equalsIgnoreCase(role)) {
            if (inst.getMasterHostIp() != "" || inst.getMasterRedisPort() != "") {
                changed = true;
            }
        } else {

            if (!equalsNullable(metric.getMasterHost(), inst.getMasterHostIp())
                    || !equalsNullable(metric.getMasterPort(), inst.getMasterRedisPort())) {
                masterHost = metric.getMasterHost();
                masterPort = metric.getMasterPort();
                changed = true;
            }
        }
        if (changed) {
            int rows=redisInstanceMapper.update(null,new LambdaUpdateWrapper<RedisInstance>()
                    .set(RedisInstance::getRole,role)
                    .set(RedisInstance::getMasterHostIp,masterHost)
                    .set(RedisInstance::getMasterRedisPort,masterPort)
                    .set(RedisInstance::getUpdatedTime,now)
                    .eq(RedisInstance::getInstanceId,instanceId)

            );
            if(rows>0) {
                log.info("节点角色更新：{}",CommonConstant.SUCCESS);
            }
            else{
                log.info("节点角色更新：{}",CommonConstant.FAILURE);
            }

        }
        //role 为MASTER再继续执行
        if (!ROLE_MASTER.equalsIgnoreCase(role)) {
            return;
        }
        RedisSwitchEvent lastEvent = redisSwitchEventService.selectLatestSwitchEvent();
        String lastMasterId = lastEvent == null ? null : lastEvent.getNewMasterId();
        //主节点没变，返回
        if (instanceId.equals(lastMasterId)) {
            return;
        }
        //无记录，插入初始基准记录
        if (lastEvent == null) {
            insertSwitchEvent("", instanceId, CommonConstant.SWITCH_INIT, now);
            log.info("[主从切换检测] 建立初始主基线: masterId={}", instanceId);
            return;
        }


        insertSwitchEvent(lastMasterId, instanceId, CommonConstant.SWITCH_AUTO, now);
        caffeineCache.invalidate(CacheKeyUtil.TOPOLOGY);
        caffeineCache.invalidate(CacheKeyUtil.NODES);
        log.info("[主从切换检测] 检测到主节点变更: {} -> {}", lastMasterId, instanceId);
    }


    private void insertSwitchEvent(String oldMasterId, String newMasterId,
                                   String switchType, LocalDateTime now) {
        RedisSwitchEvent event = new RedisSwitchEvent();

        event.setOldMasterId(oldMasterId);
        event.setNewMasterId(newMasterId);
        event.setSwitchTime(now);
        event.setSwitchType(switchType);
        event.setSwitchResult(CommonConstant.SWITCH_SUCCESS);
        event.setCreatedTime(now);
        event.setUpdatedTime(now);
        switchEventMapper.insert(event);

        /* 检测到主节点变更时，除失效拓扑/节点状态缓存外，同时生成告警记录 */
        if (switchType.equals(CommonConstant.SWITCH_AUTO)) {
            generateSwitchAlarm(oldMasterId, newMasterId, now);
        }
    }

    /**
     * 主从切换生成告警记录
     * @param oldMasterId
     * @param newMasterId
     * @param now
     */
    private void generateSwitchAlarm(String oldMasterId, String newMasterId, LocalDateTime now) {
        try {
            AlarmRecord record = new AlarmRecord();
            record.setAlarmNo("AL" + IdUtil.getSnowflakeNextIdStr()
                    + String.format("%03d", (int) (Math.random() * 1000)));
            // 系统切换事件，不关联具体规则
            record.setRuleId("");
            record.setInstanceId(newMasterId);
            record.setMetricKey(CommonConstant.MASTER_SWITCH);
            record.setMetricName(CommonConstant.MASTER_SWITCH_DESC);
            record.setAlarmLevel(AlarmLevelEnum.WARNING.getCode());
            record.setAlarmContent("检测到主从切换：原主节点 " + (oldMasterId == null ? "null" : oldMasterId)
                    + " → 新主节点 " + newMasterId);
            record.setTriggerValue("");
            record.setUnit("");
            record.setTriggerTime(now);
            record.setRecoverStatus(CommonConstant.UNRECOVER);
            record.setCreatedTime(now);
            record.setUpdatedTime(now);
            alarmRecordMapper.insert(record);

            log.info("[主从切换检测] 已生成切换告警记录, alarmNo={}", record.getAlarmNo());
        } catch (Exception e) {
            log.error("[主从切换检测] 生成切换告警记录失败, newMasterId={}", newMasterId, e);
        }
    }

    private boolean equalsNullable(String a, String b) {
        if (a == null) {
            return b == null;
        }
        return a.equals(b);
    }

    /**
     * 按实例取最新一条指标，拓扑节点状态判断用
     * @param instanceId
     * @return
     */
    @Override
    public MetricLog getLatestByInstanceId(String instanceId) {
        MetricLog metricLog = metricLogMapper.selectOne(Wrappers.<MetricLog>lambdaQuery().eq(MetricLog::getInstanceId, instanceId));
        return metricLog;
    }

    @Override
    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanExpiredData() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(retainDays);

        int rowsM = metricLogMapper.delete(new LambdaQueryWrapper<MetricLog>()
                        .lt(MetricLog::getTm, threshold));
        if (rowsM>0) {
            log.info("采集数据表rms_metric_log定期清理结果：{}", CommonConstant.SUCCESS);
        }
        else{
            log.info("采集数据表rms_metric_log定期清理结果：{}", CommonConstant.FAILURE);

        }

        int rowsN = nodeStatusMapper.delete(new LambdaQueryWrapper<NodeStatus>()
                .lt(NodeStatus::getTm, threshold));

        if (rowsN>0) {
            log.info("节点状态表rms_node_status_log定期清理结果：{}", CommonConstant.SUCCESS);
        }
        else{
            log.info("节点状态表rms_node_status_log定期清理结果：{}", CommonConstant.FAILURE);

        }


    }
}