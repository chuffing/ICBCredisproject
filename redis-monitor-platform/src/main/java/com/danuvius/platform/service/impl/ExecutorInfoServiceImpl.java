package com.danuvius.platform.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.danuvius.platform.common.constant.CommonConstant;
import com.danuvius.platform.common.exception.BusinessCode;
import com.danuvius.platform.common.exception.BusinessException;
import com.danuvius.platform.mapper.ExecutorInfoMapper;
import com.danuvius.platform.mapper.RedisInstanceMapper;
import com.danuvius.platform.model.dto.agent.AgentRegisterResultDTO;
import com.danuvius.platform.model.entity.ExecutorInfo;
import com.danuvius.platform.model.entity.RedisInstance;
import com.danuvius.platform.service.AuditLogService;
import com.danuvius.platform.service.ExecutorInfoService;
import com.danuvius.platform.service.RedisInstanceService;
import com.danuvius.platform.util.CacheKeyUtil;
import com.github.benmanes.caffeine.cache.Cache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
public class ExecutorInfoServiceImpl extends ServiceImpl<ExecutorInfoMapper, ExecutorInfo>
        implements ExecutorInfoService {

    private final ExecutorInfoMapper executorInfoMapper;
    private final RedisInstanceMapper redisInstanceMapper;
    private final RedisInstanceService redisInstanceService;
    private final AuditLogService auditLogService;
    private final Cache<String, Object> caffeineCache;

    @Value("${platform.register-code}")
    private List<String> validRegisterCodes;

    /** 运行状态离线阈值：连续未收到心跳/上报超过该秒数判为离线 */
    @Value("${platform.offline-threshold:70}")
    private long offlineThreshold;

    public ExecutorInfoServiceImpl(ExecutorInfoMapper executorInfoMapper, RedisInstanceMapper redisInstanceMapper,
                                   RedisInstanceService redisInstanceService,
                                   AuditLogService auditLogService,
                                   Cache<String, Object> caffeineCache) {
        this.executorInfoMapper = executorInfoMapper;
        this.redisInstanceMapper = redisInstanceMapper;
        this.redisInstanceService = redisInstanceService;
        this.auditLogService = auditLogService;
        this.caffeineCache = caffeineCache;
    }


    /**
     * 注册分两阶段，根据redisReady判断
     * redisReady=false / null：只注册执行器，不检查 redisInfo、不建实例
     * redisReady=true 且 redisInfo 非空：
     *      一阶段注册：未绑定实例-》注册执行器+建实例绑定
     *      二阶段注册：未绑定实例-》建实例绑定
     *      重复调用注册：已绑定实例-》直接返回已就绪
     * redisReady=true 但 redisInfo 为空：不绑定，返回未就绪
     */
    @Override
    public AgentRegisterResultDTO register(String registerCode,
                                           String hostIp, String executorPort,
                                           Map<String, String> redisInfo,
                                           Boolean redisReady) {
        validateRegisterCode(registerCode);

        // 就绪判定：redisReady=true+redisInfo非空
        Boolean readyToBind = Boolean.TRUE.equals(redisReady) && hasRedisInfo(redisInfo);

        //查是否已有执行器
        ExecutorInfo existing = executorInfoMapper.selectOne(Wrappers.<ExecutorInfo>lambdaQuery()
                .eq(ExecutorInfo::getHostIp, hostIp)
                .eq(ExecutorInfo::getExecutorPort, executorPort)
        );

        //已注册执行器
        if (existing != null) {
            // 已注册：仅在"未绑定 + 具备绑定条件"时才绑定
            if (!isBound(existing) && readyToBind) {
                bindInstance(existing, redisInfo);
            }
            log.info("Agent 重复注册: 执行器id={}, hostIp={}, redisReady={}, redis是否bind={}",
                    existing.getId(), hostIp, redisReady, isBound(existing));
            return buildResult(existing);
        }

        // 首次注册：只建执行器；redisReady=false 时不看 redisInfo
        ExecutorInfo agent = createExecutor(hostIp, executorPort);
        if (readyToBind) {
            //建redis实例表记录
            bindInstance(agent, redisInfo);
        }
        log.info("Agent 首次注册: 执行器id={}, hostIp={}, redisReady={}, bind={}",
                agent.getId(), hostIp, redisReady, isBound(agent));
        return buildResult(agent);
    }

    @Override
    public Boolean heartbeat(String executorToken, String hostIp, String executorPort) {
        ExecutorInfo agent = executorInfoMapper.selectOne(Wrappers.<ExecutorInfo>lambdaQuery()
                .eq(ExecutorInfo::getExecutorToken, executorToken)
        );
        if (agent == null ) {
            throw new BusinessException(BusinessCode.PARAMS_ERROR.getCode(),"无效的注册码");
        }
        if (!hostIp.equals(agent.getHostIp()) || !executorPort.equals(agent.getExecutorPort())) {
            throw new BusinessException(BusinessCode.PARAMS_ERROR.getCode(),"Agent 身份不匹配");
        }

        LocalDateTime now = LocalDateTime.now();
        //更新执行器状态字段
        int rows=executorInfoMapper.update(null,new LambdaUpdateWrapper<ExecutorInfo>()
                .set(ExecutorInfo::getLastHeartbeatTime,now)
                .set(ExecutorInfo::getExecutorStatus,CommonConstant.ONLINE)
                .set(ExecutorInfo::getUpdatedTime,now)
                .eq(ExecutorInfo::getExecutorToken,executorToken)
        );
        /* 执行器30s心跳时同步更新其绑定实例的最后心跳时间与运行状态 */
        if(rows>0) {
            log.info("执行器心跳更新:{}",CommonConstant.SUCCESS);
            // 心跳会刷新实例在线状态，失效实例列表缓存，避免离线实例长时间显示在线
            caffeineCache.invalidate(CacheKeyUtil.instanceList(CommonConstant.MANAGE_ACTIVE));
            caffeineCache.invalidate(CacheKeyUtil.instanceList(CommonConstant.MANAGE_INACTIVE));
            // 已绑定时同步刷新实例心跳与运行状态；未绑定（S1 阶段）跳过
            if (isBound(agent)) {
                redisInstanceService.updateHeartbeatAndStatus(agent.getInstanceId(), now,
                        CommonConstant.RUN_NORMAL);
            }
            return true;
        }
        else{
            log.info("执行器心跳更新:{}",CommonConstant.FAILURE);
            return false;

        }
    }

    /**
     * 定时校正执行器与 Redis 实例的在线状态
     *
     * 执行器每 30s 发送一次心跳；如果进程退出、网络中断或机器宕机，
     * 心跳停止后仅靠前端查询时的动态判断会受缓存影响，无法及时落库
     * 按固定间隔主动扫描，超过离线阈值未心跳status设置0
     */
    @Scheduled(fixedDelayString = "${platform.status-check.fixed-delay-ms:30000}")
    public void correctOfflineStatus() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime before = now.minusSeconds(offlineThreshold);

        // 执行器心跳超时：只更新当前仍为在线或状态为空的记录，避免重复 UPDATE 在线记录
        int executorRows = executorInfoMapper.update(null, new LambdaUpdateWrapper<ExecutorInfo>()
                .set(ExecutorInfo::getExecutorStatus, CommonConstant.OFFLINE)
                .set(ExecutorInfo::getUpdatedTime, now)
                .and(w -> w.eq(ExecutorInfo::getExecutorStatus, CommonConstant.ONLINE)
                        .or()
                        .isNull(ExecutorInfo::getExecutorStatus))
                .and(w -> w.lt(ExecutorInfo::getLastHeartbeatTime, before)
                        .or()
                        .isNull(ExecutorInfo::getLastHeartbeatTime))
        );

        // Redis 实例心跳/上报超时：执行器断联后，其绑定实例也会随之超时
        int instanceRows = redisInstanceMapper.update(null, new LambdaUpdateWrapper<RedisInstance>()
                .set(RedisInstance::getRunStatus, CommonConstant.RUN_OFFLINE)
                .set(RedisInstance::getUpdatedTime, now)
                .and(w -> w.ne(RedisInstance::getRunStatus, CommonConstant.RUN_OFFLINE)
                        .or()
                        .isNull(RedisInstance::getRunStatus))
                .and(w -> w.lt(RedisInstance::getLastHeartbeatTime, before)
                        .or()
                        .isNull(RedisInstance::getLastHeartbeatTime))
        );

        if (executorRows > 0 || instanceRows > 0) {
            // 数据库状态已变化，失效相关读缓存，避免前端继续展示在线
            caffeineCache.invalidate(CacheKeyUtil.instanceList(CommonConstant.MANAGE_ACTIVE));
            caffeineCache.invalidate(CacheKeyUtil.instanceList(CommonConstant.MANAGE_INACTIVE));
            caffeineCache.invalidate(CacheKeyUtil.TOPOLOGY);
            caffeineCache.invalidate(CacheKeyUtil.NODES);
            log.info("[状态校正] 执行器离线 {} 个，Redis实例离线 {} 个", executorRows, instanceRows);
        } else {
            log.debug("[状态校正] 无超时执行器/实例");
        }
    }

    /**
     * 批量变更执行器纳管状态，并同步其绑定 Redis 实例的纳管状态
     *
     * 前端默认实例列表/拓扑/报告只查询 management_status=1；执行器注册时默认为 0
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateManagementStatus(List<String> executorIds, String managementStatus) {
        List<ExecutorInfo> executors = listByIds(executorIds);
        if (executors == null || executors.isEmpty()) {
            throw new BusinessException(BusinessCode.NOT_FOUND_ERROR.getCode(), "执行器不存在");
        }

        Set<String> foundIds = new LinkedHashSet<>();
        Set<String> instanceIds = new LinkedHashSet<>();
        for (ExecutorInfo executor : executors) {
            if (executor.getId() != null && !executor.getId().trim().isEmpty()) {
                foundIds.add(executor.getId());
            }
            if (isBound(executor)) {
                instanceIds.add(executor.getInstanceId().trim());
            }
        }
        if (foundIds.isEmpty()) {
            throw new BusinessException(BusinessCode.NOT_FOUND_ERROR.getCode(), "执行器不存在");
        }

        LocalDateTime now = LocalDateTime.now();
        int executorRows = executorInfoMapper.update(null, new LambdaUpdateWrapper<ExecutorInfo>()
                .set(ExecutorInfo::getManagementStatus, managementStatus)
                .set(ExecutorInfo::getUpdatedTime, now)
                .in(ExecutorInfo::getId, foundIds)
        );

        int instanceRows = 0;
        if (!instanceIds.isEmpty()) {
            instanceRows = redisInstanceMapper.update(null, new LambdaUpdateWrapper<RedisInstance>()
                    .set(RedisInstance::getManagementStatus, managementStatus)
                    .set(RedisInstance::getUpdatedTime, now)
                    .in(RedisInstance::getInstanceId, instanceIds)
            );
        }

        // 纳管状态变化会影响实例列表、拓扑与节点状态表格
        caffeineCache.invalidate(CacheKeyUtil.instanceList(CommonConstant.MANAGE_ACTIVE));
        caffeineCache.invalidate(CacheKeyUtil.instanceList(CommonConstant.MANAGE_INACTIVE));
        caffeineCache.invalidate(CacheKeyUtil.TOPOLOGY);
        caffeineCache.invalidate(CacheKeyUtil.NODES);
        caffeineCache.invalidate(CacheKeyUtil.sentinelList(CommonConstant.MANAGE_ACTIVE));
        caffeineCache.invalidate(CacheKeyUtil.sentinelList(CommonConstant.MANAGE_INACTIVE));

        String statusDesc = CommonConstant.MANAGE_ACTIVE.equals(managementStatus)
                ? CommonConstant.MANAGE_ACTIVE_DESC : CommonConstant.MANAGE_INACTIVE_DESC;
        String targetId = foundIds.size() == 1
                ? foundIds.iterator().next()
                : foundIds.iterator().next() + "等" + foundIds.size() + "个";
        auditLogService.record(CommonConstant.MODULE_OPERATIONS, CommonConstant.OPERATION_TYPE_UPDATE,
                targetId, "变更执行器纳管状态为" + statusDesc
                        + "，执行器 " + executorRows + " 个，实例 " + instanceRows + " 个");

        log.info("执行器纳管状态变更: status={}, executors={}, instances={}",
                managementStatus, executorRows, instanceRows);
        return executorRows;
    }


    /*注册码校验*/
    private void validateRegisterCode(String registerCode) {
        if(validRegisterCodes ==null || validRegisterCodes.isEmpty()){
            throw new BusinessException(BusinessCode.NOT_FOUND_ERROR.getCode(), "平台比对注册码缺失");
        }

        if (!validRegisterCodes.contains(registerCode)) {
            throw new BusinessException(BusinessCode.PARAMS_ERROR.getCode(), "无效的注册码");
        }
    }

    /** 首次注册：instanceId 为空串，Redis 未就绪 */
    private ExecutorInfo createExecutor(String hostIp, String executorPort) {
        ExecutorInfo agent = new ExecutorInfo();
        agent.setInstanceId("");
        agent.setHostIp(hostIp);
        agent.setExecutorPort(executorPort);
        agent.setExecutorToken(IdUtil.simpleUUID());
        agent.setExecutorStatus(CommonConstant.ONLINE);
        // 新注册执行器默认未纳管，平台变更纳管状态手动纳管
        agent.setManagementStatus(CommonConstant.MANAGE_INACTIVE);
        LocalDateTime now = LocalDateTime.now();
        agent.setLastHeartbeatTime(now);
        agent.setCreatedTime(now);
        agent.setUpdatedTime(now);
        executorInfoMapper.insert(agent);
        return agent;
    }

    /**
     * 绑定实例，建redis实例表记录+更新执行器表实例id字段，若更新失败说明并发绑定，回滚刚建的实例并读取最新绑定结果
     */
    private void bindInstance(ExecutorInfo agent, Map<String, String> redisInfo) {
        String instanceId = IdUtil.getSnowflakeNextIdStr();
        RedisInstance instance = buildInstance(instanceId, agent.getHostIp(), redisInfo,
                agent.getManagementStatus());

        // 同名实例已存在（例如平台数据被手工清理后执行器再次注册）：复用已有实例，避免唯一键冲突
        RedisInstance sameName = redisInstanceMapper.selectOne(Wrappers.<RedisInstance>lambdaQuery()
                .eq(RedisInstance::getInstanceName, instance.getInstanceName())
                .last("LIMIT 1"));
        if (sameName != null) {
            instanceId = sameName.getInstanceId();
            instance = sameName;
            // 复用已存在实例时，若执行器已纳管，同步把实例置为已纳管
            if (CommonConstant.MANAGE_ACTIVE.equals(agent.getManagementStatus())) {
                redisInstanceMapper.update(null, new LambdaUpdateWrapper<RedisInstance>()
                        .set(RedisInstance::getManagementStatus, CommonConstant.MANAGE_ACTIVE)
                        .set(RedisInstance::getUpdatedTime, LocalDateTime.now())
                        .eq(RedisInstance::getInstanceId, instanceId));
            }
        } else {
            redisInstanceMapper.insert(instance);
        }

        int rows = executorInfoMapper.update(null, Wrappers.<ExecutorInfo>lambdaUpdate()
                .set(ExecutorInfo::getInstanceId, instanceId)
                .set(ExecutorInfo::getUpdatedTime, LocalDateTime.now())
                .eq(ExecutorInfo::getId, agent.getId())
                // 兼容旧库：未绑定语义可能是 NULL 或空串
                .and(w -> w.eq(ExecutorInfo::getInstanceId, "").or().isNull(ExecutorInfo::getInstanceId))
        );

        if (rows == 0) {
            // 已被其他请求绑定：回滚新建实例，重新读取最新绑定
            if (sameName == null) {
                redisInstanceMapper.deleteById(instance.getId());
            }
            ExecutorInfo latest = executorInfoMapper.selectById(agent.getId());
            if (latest != null) {
                agent.setInstanceId(latest.getInstanceId());
            }
            log.warn("执行器 {} 并发绑定冲突，已回滚新建实例 {}", agent.getId(), instanceId);
            return;
        }
        agent.setInstanceId(instanceId);

        // 新实例纳入监控后，实例列表/拓扑/节点状态缓存立即失效，前端无需等待缓存过期
        caffeineCache.invalidate(CacheKeyUtil.instanceList(CommonConstant.MANAGE_ACTIVE));
        caffeineCache.invalidate(CacheKeyUtil.instanceList(CommonConstant.MANAGE_INACTIVE));
        caffeineCache.invalidate(CacheKeyUtil.TOPOLOGY);
        caffeineCache.invalidate(CacheKeyUtil.NODES);
        log.info("执行器 {} 绑定实例成功: {}", agent.getId(), instanceId);
    }

    /*建redis实体*/
    private RedisInstance buildInstance(String instanceId, String fallbackHostIp,
                                        Map<String, String> redisInfo, String managementStatus) {
        RedisInstance instance = new RedisInstance();
        instance.setInstanceId(instanceId);

        String ip = redisInfo.getOrDefault("host", fallbackHostIp);
        String port = redisInfo.getOrDefault("tcp_port", CommonConstant.DEFAULT_REDIS_PORT);
        instance.setInstanceName(ip + ":" + port);
        instance.setHostIp(ip);
        instance.setRedisPort(port);

        String role = redisInfo.getOrDefault("role", CommonConstant.DEFAULT_ROLE_MASTER).toUpperCase();
        instance.setRole(role);

        // 主节点没有master_host和master_port，数据库统一保存为空串。
        instance.setMasterHostIp("");
        instance.setMasterRedisPort("");
        if (CommonConstant.DEFAULT_ROLE_REPLICA.equals(role)) {
            String masterHostIp = redisInfo.get("master_host");
            String masterRedisPort = redisInfo.get("master_port");
            instance.setMasterHostIp(masterHostIp == null ? "" : masterHostIp);
            instance.setMasterRedisPort(masterRedisPort == null ? "" : masterRedisPort);
        }
        instance.setRedisVersion(redisInfo.getOrDefault("version", ""));
        instance.setRunStatus(CommonConstant.RUN_NORMAL);
        // 平台手动纳管
        instance.setManagementStatus(CommonConstant.MANAGE_INACTIVE);
        LocalDateTime now = LocalDateTime.now();
        instance.setLastHeartbeatTime(now);
        instance.setCreatedTime(now);
        instance.setUpdatedTime(now);
        return instance;
    }

    /*返回结构构造*/
    private AgentRegisterResultDTO buildResult(ExecutorInfo agent) {
        return new AgentRegisterResultDTO(agent.getExecutorToken(),
                isBound(agent) ? agent.getInstanceId() : null);
    }

    /*执行器记录存在且实例id非空串，则已绑定*/
    private Boolean isBound(ExecutorInfo agent) {
        return agent != null
                && agent.getInstanceId() != null
                && !agent.getInstanceId().trim().isEmpty();
    }

    /*判断是否存在redisInfo */
    private Boolean hasRedisInfo(Map<String, String> redisInfo) {
        return redisInfo != null && !redisInfo.isEmpty();
    }


}
