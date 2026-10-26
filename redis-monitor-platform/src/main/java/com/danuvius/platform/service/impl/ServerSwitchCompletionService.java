package com.danuvius.platform.service.impl;

import cn.hutool.core.util.IdUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.danuvius.platform.common.constant.ServerSwitchConstants;
import com.danuvius.platform.common.exception.BusinessCode;
import com.danuvius.platform.common.exception.BusinessException;
import com.danuvius.platform.model.entity.ExecutorInfo;
import com.danuvius.platform.model.entity.OperationTask;
import com.danuvius.platform.model.entity.RedisInstance;
import com.danuvius.platform.model.entity.SentinelNodeInfo;
import com.danuvius.platform.model.enums.OperationTaskTypeEnum;
import com.danuvius.platform.model.enums.StatusEnum;
import com.danuvius.platform.service.ExecutorInfoService;
import com.danuvius.platform.service.NodeConfigFileService;
import com.danuvius.platform.service.RedisInstanceService;
import com.danuvius.platform.service.SentinelNodeInfoService;
import com.danuvius.platform.util.CacheKeyUtil;
import com.danuvius.platform.util.StringUtils;
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
    private static final String MASTER_ROLE = "MASTER";

    private final RedisInstanceService redisInstanceService;

    private final SentinelNodeInfoService sentinelNodeInfoService;

    private final ExecutorInfoService executorInfoService;

    private final NodeConfigFileService configFileService;

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

        // 1. 读取并检查创建任务时保存的服务器切换快照。
        JSONObject snapshot;
        try {
            snapshot = JSON.parseObject(task.getRequestParams());
        } catch (Exception exception) {
            throw new BusinessException(
                    BusinessCode.OPERATION_ERROR.getCode(),
                    "服务器切换任务快照无法解析"
            );
        }

        if (snapshot == null) {
            throw new BusinessException(
                    BusinessCode.OPERATION_ERROR.getCode(),
                    "服务器切换任务快照不能为空"
            );
        }

        String targetVersion = StringUtils.trimToNull(
                snapshot.getString(
                        ServerSwitchConstants.TARGET_VERSION_PARAM
                )
        );
        String masterHostIp = StringUtils.trimToNull(
                snapshot.getString(
                        ServerSwitchConstants.MASTER_HOST_IP_PARAM
                )
        );
        JSONArray sourceNodes = snapshot.getJSONArray(
                ServerSwitchConstants.SOURCE_NODES_PARAM
        );
        JSONArray targetNodes = snapshot.getJSONArray(
                ServerSwitchConstants.TARGET_NODES_PARAM
        );

        if (targetVersion == null
                || masterHostIp == null
                || sourceNodes == null
                || sourceNodes.size()
                != ServerSwitchConstants.TARGET_NODE_COUNT
                || targetNodes == null
                || targetNodes.size()
                != ServerSwitchConstants.TARGET_NODE_COUNT) {
            throw new BusinessException(
                    BusinessCode.OPERATION_ERROR.getCode(),
                    "服务器切换任务快照不完整"
            );
        }

        // 2. 从任务快照中取得新旧服务器和执行器信息。
        Set<String> sourceExecutorIds = new HashSet<>();
        for (int index = 0; index < sourceNodes.size(); index++) {
            sourceExecutorIds.add(requiredValue(
                    sourceNodes.getJSONObject(index),
                    ServerSwitchConstants.EXECUTOR_ID_PARAM
            ));
        }

        List<String> targetHostIps = new ArrayList<>();
        Set<String> targetExecutorIds = new HashSet<>();
        for (int index = 0; index < targetNodes.size(); index++) {
            JSONObject targetNode = targetNodes.getJSONObject(index);
            targetHostIps.add(requiredValue(
                    targetNode,
                    ServerSwitchConstants.HOST_IP_PARAM
            ));
            targetExecutorIds.add(requiredValue(
                    targetNode,
                    ServerSwitchConstants.EXECUTOR_ID_PARAM
            ));
        }

        if (sourceExecutorIds.size()
                != ServerSwitchConstants.TARGET_NODE_COUNT
                || new HashSet<>(targetHostIps).size()
                != ServerSwitchConstants.TARGET_NODE_COUNT
                || targetExecutorIds.size()
                != ServerSwitchConstants.TARGET_NODE_COUNT) {
            throw new BusinessException(
                    BusinessCode.OPERATION_ERROR.getCode(),
                    "服务器切换任务快照中的节点信息重复"
            );
        }

        // 3. 先将目标服务器配置复制到current目录。
        // 数据库后续更新失败时，多出的配置不会自动获得纳管资格。
        configFileService.copyStagingConfigsToCurrent(targetHostIps);

        LocalDateTime now = LocalDateTime.now();

        // 4. 将当前正式Redis和Sentinel退出纳管。
        List<RedisInstance> activeRedisInstances = redisInstanceService.list(
                new LambdaQueryWrapper<RedisInstance>().eq(
                        RedisInstance::getManagementStatus,
                        StatusEnum.ACTIVE.getCode()
                )
        );
        for (RedisInstance redisInstance : activeRedisInstances) {
            redisInstance.setManagementStatus(
                    StatusEnum.INACTIVE.getCode()
            );
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

        List<SentinelNodeInfo> activeSentinelNodes =
                sentinelNodeInfoService.list(
                        new LambdaQueryWrapper<SentinelNodeInfo>().eq(
                                SentinelNodeInfo::getManagementStatus,
                                StatusEnum.ACTIVE.getCode()
                        )
                );
        for (SentinelNodeInfo sentinelNode : activeSentinelNodes) {
            sentinelNode.setManagementStatus(
                    StatusEnum.INACTIVE.getCode()
            );
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

        // 5. 将三台旧服务器上的执行器退出纳管。
        List<ExecutorInfo> sourceExecutors = executorInfoService.list(
                new LambdaQueryWrapper<ExecutorInfo>().in(
                        ExecutorInfo::getId,
                        sourceExecutorIds
                )
        );
        if (sourceExecutors.size() != sourceExecutorIds.size()) {
            throw new BusinessException(
                    BusinessCode.OPERATION_ERROR.getCode(),
                    "服务器切换任务中的旧执行器记录不完整"
            );
        }

        for (ExecutorInfo executor : sourceExecutors) {
            executor.setManagementStatus(StatusEnum.INACTIVE.getCode());
            executor.setUpdatedTime(now);

            if (!executorInfoService.updateById(executor)) {
                throw new BusinessException(
                        BusinessCode.OPERATION_ERROR.getCode(),
                        "旧执行器退出纳管失败："
                                + executor.getId()
                );
            }
        }

        // 6. 查询三台目标服务器可能存在的历史Redis记录。
        List<RedisInstance> targetRedisInstances = redisInstanceService.list(
                new LambdaQueryWrapper<RedisInstance>().in(
                        RedisInstance::getHostIp,
                        targetHostIps
                )
        );
        Map<String, RedisInstance> redisByHost = new HashMap<>();
        for (RedisInstance redisInstance : targetRedisInstances) {
            String hostIp = StringUtils.trimToNull(
                    redisInstance.getHostIp()
            );
            if (hostIp == null
                    || redisByHost.put(hostIp, redisInstance) != null) {
                throw new BusinessException(
                        BusinessCode.OPERATION_ERROR.getCode(),
                        "同一目标服务器存在多条Redis记录：" + hostIp
                );
            }
        }

        // 7. 查询三台目标服务器可能存在的历史Sentinel记录。
        List<SentinelNodeInfo> targetSentinelNodes =
                sentinelNodeInfoService.list(
                        new LambdaQueryWrapper<SentinelNodeInfo>().in(
                                SentinelNodeInfo::getNodeIp,
                                targetHostIps
                        )
                );
        Map<String, SentinelNodeInfo> sentinelByHost = new HashMap<>();
        for (SentinelNodeInfo sentinelNode : targetSentinelNodes) {
            String hostIp = StringUtils.trimToNull(
                    sentinelNode.getNodeIp()
            );
            if (hostIp == null
                    || sentinelByHost.put(hostIp, sentinelNode) != null) {
                throw new BusinessException(
                        BusinessCode.OPERATION_ERROR.getCode(),
                        "同一目标服务器存在多条Sentinel记录：" + hostIp
                );
            }
        }

        // 8. 查询三台目标服务器上的执行器。
        List<ExecutorInfo> targetExecutors = executorInfoService.list(
                new LambdaQueryWrapper<ExecutorInfo>().in(
                        ExecutorInfo::getId,
                        targetExecutorIds
                )
        );
        if (targetExecutors.size() != targetExecutorIds.size()) {
            throw new BusinessException(
                    BusinessCode.OPERATION_ERROR.getCode(),
                    "服务器切换任务中的目标执行器记录不完整"
            );
        }

        Map<String, ExecutorInfo> executorById = new HashMap<>();
        for (ExecutorInfo executor : targetExecutors) {
            executorById.put(executor.getId(), executor);
        }

        // 9. 创建或更新三台目标服务器的正式拓扑记录。
        for (int index = 0; index < targetNodes.size(); index++) {
            JSONObject targetNode = targetNodes.getJSONObject(index);

            String hostIp = requiredValue(
                    targetNode,
                    ServerSwitchConstants.HOST_IP_PARAM
            );
            String role = requiredValue(
                    targetNode,
                    ServerSwitchConstants.ROLE_PARAM
            );
            String executorId = requiredValue(
                    targetNode,
                    ServerSwitchConstants.EXECUTOR_ID_PARAM
            );
            String redisConfigSha256 = requiredValue(
                    targetNode,
                    ServerSwitchConstants.REDIS_CONFIG_SHA256_PARAM
            );
            String sentinelConfigSha256 = requiredValue(
                    targetNode,
                    ServerSwitchConstants.SENTINEL_CONFIG_SHA256_PARAM
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
                    MASTER_ROLE.equals(role) ? "" : masterHostIp
            );
            redisInstance.setMasterRedisPort(
                    MASTER_ROLE.equals(role) ? "" : REDIS_PORT
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
            sentinelNode.setNodeIp(hostIp);
            sentinelNode.setNodePort(SENTINEL_PORT);
            sentinelNode.setSentinelStatus(StatusEnum.ONLINE.getCode());
            sentinelNode.setMasterHostIp(masterHostIp);
            sentinelNode.setMasterRedisPort(REDIS_PORT);
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
            ExecutorInfo executor = executorById.get(executorId);
            executor.setInstanceId(redisInstance.getInstanceId());
            executor.setManagementStatus(StatusEnum.ACTIVE.getCode());
            executor.setUpdatedTime(now);

            if (!executorInfoService.updateById(executor)) {
                throw new BusinessException(
                        BusinessCode.OPERATION_ERROR.getCode(),
                        "目标执行器纳管关系更新失败：" + executorId
                );
            }
        }

        // 10. 数据库事务提交成功后清理缓存。
        // 页面下一次查询时，直接读取新的正式纳管关系。
        clearTopologyCacheAfterCommit();
    }

    /**
     * 数据库事务提交成功后清理拓扑缓存。
     */
    private void clearTopologyCacheAfterCommit() {

        // 当前方法正常情况下由finishStage事务调用。
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

        // 如果以后在没有事务的地方调用，数据库操作已经直接提交，
        // 此时立即清理缓存。
        clearTopologyCache();
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

    /**
     * 从服务器切换任务快照中读取必填字符串。
     */
    private String requiredValue(JSONObject object, String name) {
        String value = object == null ? null
                : StringUtils.trimToNull(object.getString(name));

        if (value == null) {
            throw new BusinessException(
                    BusinessCode.OPERATION_ERROR.getCode(),
                    "服务器切换任务快照缺少参数：" + name
            );
        }
        return value;
    }
}


