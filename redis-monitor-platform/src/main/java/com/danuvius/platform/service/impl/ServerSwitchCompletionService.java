package com.danuvius.platform.service.impl;

import cn.hutool.core.util.IdUtil;
import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.danuvius.platform.common.constant.ServerSwitchConstants;
import com.danuvius.platform.common.exception.BusinessCode;
import com.danuvius.platform.common.exception.BusinessException;
import com.danuvius.platform.mapper.RedisVersionPackageMapper;
import com.danuvius.platform.model.dto.ServerSwitchRequestDTO;
import com.danuvius.platform.model.entity.ExecutorInfo;
import com.danuvius.platform.model.entity.OperationTask;
import com.danuvius.platform.model.entity.RedisInstance;
import com.danuvius.platform.model.entity.RedisVersionPackage;
import com.danuvius.platform.model.entity.SentinelNodeInfo;
import com.danuvius.platform.model.enums.OperationTaskTypeEnum;
import com.danuvius.platform.model.enums.StatusEnum;
import com.danuvius.platform.service.ExecutorInfoService;
import com.danuvius.platform.service.NodeConfigFileService;
import com.danuvius.platform.service.RedisInstanceService;
import com.danuvius.platform.service.SentinelNodeInfoService;
import com.danuvius.platform.util.CacheKeyUtil;
import com.github.benmanes.caffeine.cache.Cache;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 服务器切换成功后，提交新的正式纳管关系。
 */
@Service
@RequiredArgsConstructor
public class ServerSwitchCompletionService {

    private static final String REDIS_PORT = "6379";
    private static final String SENTINEL_PORT = "26379";
    private final RedisInstanceService redisInstanceService;

    private final SentinelNodeInfoService sentinelNodeInfoService;

    private final ExecutorInfoService executorInfoService;

    private final NodeConfigFileService configFileService;

    private final RedisVersionPackageMapper versionPackageMapper;

    private final Cache<String, Object> caffeineCache;

    /**
     * 最后一个任务阶段成功后，提交服务器切换产生的配置和纳管关系。
     *
     * <p>该方法由运维任务完成事务调用。数据库更新失败时可以回滚，
     * 已经复制到current的配置不会自动生效，正式纳管关系仍以数据库为准。</p>
     *
     * @param task 已经完成全部执行阶段的运维任务
     */
    public void completeIfNecessary(OperationTask task) {

        // 不是服务器切换任务，不需要修改服务器纳管关系。
        if (!OperationTaskTypeEnum.SERVER_SWITCH.name()
                .equals(task.getTaskType())) {
            return;
        }

        // 1. 读取创建任务时保存的前端请求参数。
        ServerSwitchRequestDTO request;
        try {
            request = JSON.parseObject(
                    task.getRequestParams(),
                    ServerSwitchRequestDTO.class
            );
            if (request == null
                    || request.getPackageId() == null
                    || request.getMasterHostIp() == null
                    || request.getReplicaHostIps() == null
                    || request.getReplicaHostIps().size()
                    != ServerSwitchConstants.REPLICA_NODE_COUNT) {
                throw new IllegalArgumentException();
            }
        } catch (Exception exception) {
            throw new BusinessException(
                    BusinessCode.OPERATION_ERROR.getCode(),
                    "服务器切换任务参数无法解析"
            );
        }

        // 2. 根据安装包记录取得本次切换的目标版本。
        RedisVersionPackage versionPackage = versionPackageMapper.selectById(
                request.getPackageId()
        );
        if (versionPackage == null) {
            throw new BusinessException(
                    BusinessCode.OPERATION_ERROR.getCode(),
                    "服务器切换任务使用的安装包记录不存在"
            );
        }
        String targetVersion = versionPackage.getRedisVersion();
        String masterHostIp = request.getMasterHostIp();

        // 3. 查询当前正式纳管的三台Redis。
        List<RedisInstance> activeRedisInstances = redisInstanceService.list(
                new LambdaQueryWrapper<RedisInstance>().eq(
                        RedisInstance::getManagementStatus,
                        StatusEnum.ACTIVE.getCode()
                )
        );
        if (activeRedisInstances.size()
                != ServerSwitchConstants.TARGET_NODE_COUNT) {
            throw new BusinessException(
                    BusinessCode.OPERATION_ERROR.getCode(),
                    "当前已纳管Redis记录不是3条"
            );
        }

        Set<String> sourceInstanceIds = new HashSet<>();
        for (RedisInstance redisInstance : activeRedisInstances) {
            sourceInstanceIds.add(redisInstance.getInstanceId());
        }

        // 4. 根据Redis实例编号查询当前正式纳管的三台执行器。
        List<ExecutorInfo> sourceExecutors = executorInfoService.list(
                new LambdaQueryWrapper<ExecutorInfo>()
                        .eq(ExecutorInfo::getManagementStatus,
                                StatusEnum.ACTIVE.getCode())
                        .in(ExecutorInfo::getInstanceId,
                                sourceInstanceIds)
        );
        if (sourceExecutors.size()
                != ServerSwitchConstants.TARGET_NODE_COUNT) {
            throw new BusinessException(
                    BusinessCode.OPERATION_ERROR.getCode(),
                    "当前已纳管Redis对应的执行器记录不完整"
            );
        }

        // 5. 查询当前正式纳管的三台Sentinel。
        List<SentinelNodeInfo> activeSentinelNodes =
                sentinelNodeInfoService.list(
                        new LambdaQueryWrapper<SentinelNodeInfo>().eq(
                                SentinelNodeInfo::getManagementStatus,
                                StatusEnum.ACTIVE.getCode()
                        )
                );
        if (activeSentinelNodes.size()
                != ServerSwitchConstants.TARGET_NODE_COUNT) {
            throw new BusinessException(
                    BusinessCode.OPERATION_ERROR.getCode(),
                    "当前已纳管Sentinel记录不是3条"
            );
        }

        // 6. 根据前端选择的三个IP查询目标执行器。
        List<String> targetHostIps = new ArrayList<>();
        targetHostIps.add(masterHostIp);
        targetHostIps.addAll(request.getReplicaHostIps());

        List<ExecutorInfo> targetExecutors = executorInfoService.list(
                new LambdaQueryWrapper<ExecutorInfo>()
                        .eq(ExecutorInfo::getManagementStatus,
                                StatusEnum.INACTIVE.getCode())
                        .in(ExecutorInfo::getHostIp, targetHostIps)
        );
        if (targetExecutors.size()
                != ServerSwitchConstants.TARGET_NODE_COUNT) {
            throw new BusinessException(
                    BusinessCode.OPERATION_ERROR.getCode(),
                    "三台目标服务器的执行器记录不完整"
            );
        }

        Map<String, ExecutorInfo> executorByHost = new HashMap<>();
        for (ExecutorInfo executor : targetExecutors) {
            executorByHost.put(executor.getHostIp(), executor);
        }

        // 7. 查询目标服务器可能存在的历史Redis和Sentinel记录。
        List<RedisInstance> targetRedisInstances = redisInstanceService.list(
                new LambdaQueryWrapper<RedisInstance>().in(
                        RedisInstance::getHostIp,
                        targetHostIps
                )
        );
        Map<String, RedisInstance> redisByHost = new HashMap<>();
        for (RedisInstance redisInstance : targetRedisInstances) {
            String hostIp = redisInstance.getHostIp();
            if (redisByHost.put(hostIp, redisInstance) != null) {
                throw new BusinessException(
                        BusinessCode.OPERATION_ERROR.getCode(),
                        "同一目标服务器存在多条Redis记录：" + hostIp
                );
            }
        }

        List<SentinelNodeInfo> targetSentinelNodes =
                sentinelNodeInfoService.list(
                        new LambdaQueryWrapper<SentinelNodeInfo>().in(
                                SentinelNodeInfo::getNodeIp,
                                targetHostIps
                        )
                );
        Map<String, SentinelNodeInfo> sentinelByHost = new HashMap<>();
        for (SentinelNodeInfo sentinelNode : targetSentinelNodes) {
            String hostIp = sentinelNode.getNodeIp();
            if (sentinelByHost.put(hostIp, sentinelNode) != null) {
                throw new BusinessException(
                        BusinessCode.OPERATION_ERROR.getCode(),
                        "同一目标服务器存在多条Sentinel记录：" + hostIp
                );
            }
        }

        // 8. 将目标配置从staging目录复制到current目录，并重新计算正式配置摘要。
        configFileService.copyStagingConfigsToCurrent(targetHostIps);

        LocalDateTime now = LocalDateTime.now();

        // 9. 将当前三台生产Redis、Sentinel和执行器退出纳管。
        for (RedisInstance redisInstance : activeRedisInstances) {
            redisInstance.setManagementStatus(StatusEnum.INACTIVE.getCode());
            redisInstance.setRunStatus(StatusEnum.RUN_OFFLINE.getCode());
            redisInstance.setUpdatedTime(now);
            if (!redisInstanceService.updateById(redisInstance)) {
                throw new BusinessException(
                        BusinessCode.OPERATION_ERROR.getCode(),
                        "旧Redis实例退出纳管失败："
                                + redisInstance.getHostIp()
                );
            }
        }

        for (SentinelNodeInfo sentinelNode : activeSentinelNodes) {
            sentinelNode.setManagementStatus(StatusEnum.INACTIVE.getCode());
            sentinelNode.setSentinelStatus(StatusEnum.OFFLINE.getCode());
            sentinelNode.setUpdatedTime(now);
            if (!sentinelNodeInfoService.updateById(sentinelNode)) {
                throw new BusinessException(
                        BusinessCode.OPERATION_ERROR.getCode(),
                        "旧Sentinel节点退出纳管失败："
                                + sentinelNode.getNodeIp()
                );
            }
        }

        for (ExecutorInfo executor : sourceExecutors) {
            executor.setManagementStatus(StatusEnum.INACTIVE.getCode());
            executor.setUpdatedTime(now);
            if (!executorInfoService.updateById(executor)) {
                throw new BusinessException(
                        BusinessCode.OPERATION_ERROR.getCode(),
                        "旧执行器退出纳管失败：" + executor.getId()
                );
            }
        }

        // 10. 创建或更新三台目标服务器的正式拓扑记录。
        for (String hostIp : targetHostIps) {
            String role = masterHostIp.equals(hostIp)
                    ? ServerSwitchConstants.MASTER_ROLE
                    : ServerSwitchConstants.REPLICA_ROLE;
            String redisConfigSha256 = configFileService.calculateSha256(
                    configFileService.getRedisConfigFile(hostIp),
                    hostIp + "服务器的Redis"
            );
            String sentinelConfigSha256 = configFileService.calculateSha256(
                    configFileService.getSentinelConfigFile(hostIp),
                    hostIp + "服务器的Sentinel"
            );

            // 创建或更新当前服务器的Redis记录。
            RedisInstance redisInstance = redisByHost.get(hostIp);
            boolean newRedisInstance = redisInstance == null;
            if (newRedisInstance) {
                redisInstance = new RedisInstance();
                redisInstance.setInstanceId(
                        IdUtil.getSnowflakeNextIdStr()
                );
                redisInstance.setCreatedTime(now);
            }

            redisInstance.setInstanceName(hostIp + ":" + REDIS_PORT);
            redisInstance.setHostIp(hostIp);
            redisInstance.setRedisPort(REDIS_PORT);
            redisInstance.setRole(role);
            redisInstance.setRedisVersion(targetVersion);
            redisInstance.setRunStatus(StatusEnum.RUN_NORMAL.getCode());
            redisInstance.setMasterHostIp(
                    ServerSwitchConstants.MASTER_ROLE.equals(role)
                            ? "" : masterHostIp
            );
            redisInstance.setMasterRedisPort(
                    ServerSwitchConstants.MASTER_ROLE.equals(role)
                            ? "" : REDIS_PORT
            );
            redisInstance.setConfigSha256(redisConfigSha256);
            redisInstance.setManagementStatus(StatusEnum.ACTIVE.getCode());
            redisInstance.setLastHeartbeatTime(now);
            redisInstance.setUpdatedTime(now);

            if (newRedisInstance) {
                if (!redisInstanceService.save(redisInstance)) {
                    throw new BusinessException(
                            BusinessCode.OPERATION_ERROR.getCode(),
                            "目标Redis实例保存失败：" + hostIp
                    );
                }
            } else if (!redisInstanceService.updateById(redisInstance)) {
                throw new BusinessException(
                        BusinessCode.OPERATION_ERROR.getCode(),
                        "目标Redis实例更新失败：" + hostIp
                );
            }

            // 创建或更新当前服务器的Sentinel记录。
            SentinelNodeInfo sentinelNode = sentinelByHost.get(hostIp);
            boolean newSentinelNode = sentinelNode == null;
            if (newSentinelNode) {
                sentinelNode = new SentinelNodeInfo();
                sentinelNode.setCreatedTime(now);
            }

            sentinelNode.setNodeName(hostIp + ":" + SENTINEL_PORT);
            sentinelNode.setInstanceId(redisInstance.getInstanceId());
            sentinelNode.setNodeIp(hostIp);
            sentinelNode.setNodePort(SENTINEL_PORT);
            sentinelNode.setSentinelStatus(StatusEnum.ONLINE.getCode());
            sentinelNode.setConfigSha256(sentinelConfigSha256);
            sentinelNode.setManagementStatus(StatusEnum.ACTIVE.getCode());
            sentinelNode.setLastHeartbeatTime(now);
            sentinelNode.setUpdatedTime(now);

            if (newSentinelNode) {
                if (!sentinelNodeInfoService.save(sentinelNode)) {
                    throw new BusinessException(
                            BusinessCode.OPERATION_ERROR.getCode(),
                            "目标Sentinel节点保存失败：" + hostIp
                    );
                }
            } else if (!sentinelNodeInfoService.updateById(sentinelNode)) {
                throw new BusinessException(
                        BusinessCode.OPERATION_ERROR.getCode(),
                        "目标Sentinel节点更新失败：" + hostIp
                );
            }

            // 将目标执行器绑定到当前服务器的Redis实例并正式纳管。
            ExecutorInfo executor = executorByHost.get(hostIp);
            executor.setInstanceId(redisInstance.getInstanceId());
            executor.setManagementStatus(StatusEnum.ACTIVE.getCode());
            executor.setUpdatedTime(now);

            if (!executorInfoService.updateById(executor)) {
                throw new BusinessException(
                        BusinessCode.OPERATION_ERROR.getCode(),
                        "目标执行器纳管关系更新失败：" + executor.getId()
                );
            }
        }

        // 11. 数据库事务提交成功后清理缓存。
        // 页面下一次查询时，直接读取新的正式纳管关系。
        clearTopologyCacheAfterCommit();
    }

    /**
     * 数据库事务提交成功后清理拓扑缓存。
     */
    private void clearTopologyCacheAfterCommit() {

        // 当前方法由finishStage（事务中）调用。
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            clearTopologyCache();
                        }
                    }
            );
            return;
        }
    }

    /**
     * 清理Redis、Sentinel和拓扑相关缓存。
     */
    private void clearTopologyCache() {
        caffeineCache.invalidate(CacheKeyUtil.instanceList(
                StatusEnum.ACTIVE.getCode()
        ));
        caffeineCache.invalidate(CacheKeyUtil.instanceList(
                StatusEnum.INACTIVE.getCode()
        ));
        caffeineCache.invalidate(CacheKeyUtil.sentinelList(
                StatusEnum.ACTIVE.getCode()
        ));
        caffeineCache.invalidate(CacheKeyUtil.sentinelList(
                StatusEnum.INACTIVE.getCode()
        ));
        caffeineCache.invalidate(CacheKeyUtil.TOPOLOGY);
        caffeineCache.invalidate(CacheKeyUtil.NODES);
    }

}


