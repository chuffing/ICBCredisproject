package com.danuvius.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.danuvius.platform.common.constant.TopologyInitializationConstants;
import com.danuvius.platform.common.exception.BusinessCode;
import com.danuvius.platform.common.exception.BusinessException;
import com.danuvius.platform.model.dto.TopologyInitializationRequestDTO;
import com.danuvius.platform.model.entity.ExecutorInfo;
import com.danuvius.platform.model.entity.RedisInstance;
import com.danuvius.platform.model.entity.SentinelNodeInfo;
import com.danuvius.platform.model.enums.StatusEnum;
import com.danuvius.platform.model.vo.TopologyInitializationCandidateVO;
import com.danuvius.platform.model.vo.TopologyInitializationResponseVO;
import com.danuvius.platform.service.ExecutorInfoService;
import com.danuvius.platform.service.NodeConfigFileService;
import com.danuvius.platform.service.RedisInstanceService;
import com.danuvius.platform.service.SentinelNodeInfoService;
import com.danuvius.platform.service.TopologyInitializationService;
import com.danuvius.platform.util.CacheKeyUtil;
import com.danuvius.platform.util.StringUtils;
import com.github.benmanes.caffeine.cache.Cache;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 首次拓扑纳管服务实现。
 */
@Service
@RequiredArgsConstructor
public class TopologyInitializationServiceImpl
        implements TopologyInitializationService {

    private final RedisInstanceService redisInstanceService;

    private final SentinelNodeInfoService sentinelNodeInfoService;

    private final ExecutorInfoService executorInfoService;

    private final NodeConfigFileService configFileService;

    private final Cache<String, Object> caffeineCache;

    /**
     * 查询注册阶段已经采集完整数据的未纳管服务器。
     */
    @Override
    public List<TopologyInitializationCandidateVO> listCandidates() {

        // 1. 查询未纳管并且在线的执行器。
        List<ExecutorInfo> executors = executorInfoService.list(
                new LambdaQueryWrapper<ExecutorInfo>()
                        .eq(ExecutorInfo::getManagementStatus,
                                StatusEnum.INACTIVE.getCode())
                        .eq(ExecutorInfo::getExecutorStatus,
                                StatusEnum.ONLINE.getCode())
        );

        // 2. 查询注册阶段已经采集到的Redis和Sentinel记录。
        List<RedisInstance> redisInstances = redisInstanceService.list(
                new LambdaQueryWrapper<RedisInstance>().eq(
                        RedisInstance::getManagementStatus,
                        StatusEnum.INACTIVE.getCode()
                )
        );
        List<SentinelNodeInfo> sentinelNodes = sentinelNodeInfoService.list(
                new LambdaQueryWrapper<SentinelNodeInfo>().eq(
                        SentinelNodeInfo::getManagementStatus,
                        StatusEnum.INACTIVE.getCode()
                )
        );

        Map<String, RedisInstance> redisByInstanceId = new HashMap<>();
        for (RedisInstance redisInstance : redisInstances) {
            String instanceId = StringUtils.trimToNull(
                    redisInstance.getInstanceId()
            );
            if (instanceId != null) {
                redisByInstanceId.put(instanceId, redisInstance);
            }
        }

        Map<String, SentinelNodeInfo> sentinelByHostIp = new HashMap<>();
        for (SentinelNodeInfo sentinelNode : sentinelNodes) {
            String hostIp = StringUtils.trimToNull(sentinelNode.getNodeIp());
            if (hostIp != null) {
                sentinelByHostIp.put(hostIp, sentinelNode);
            }
        }

        // 3. 只返回执行器、Redis和Sentinel三类数据都完整的服务器。
        List<TopologyInitializationCandidateVO> candidates =
                new ArrayList<>();
        Set<String> addedHostIps = new HashSet<>();
        for (ExecutorInfo executor : executors) {
            String hostIp = StringUtils.trimToNull(executor.getHostIp());
            String instanceId = StringUtils.trimToNull(
                    executor.getInstanceId()
            );
            RedisInstance redisInstance = redisByInstanceId.get(instanceId);
            SentinelNodeInfo sentinelNode = sentinelByHostIp.get(hostIp);

            if (!isCompleteExecutor(executor)
                    || redisInstance == null
                    || sentinelNode == null
                    || !hostIp.equals(redisInstance.getHostIp())
                    || !StatusEnum.RUN_NORMAL.getCode().equals(
                    redisInstance.getRunStatus())
                    || !StatusEnum.ONLINE.getCode().equals(
                    sentinelNode.getSentinelStatus())
                    || !addedHostIps.add(hostIp)) {
                continue;
            }

            candidates.add(new TopologyInitializationCandidateVO(
                    hostIp,
                    executor.getId(),
                    executor.getExecutorPort(),
                    executor.getExecutorStatus(),
                    executor.getLastHeartbeatTime(),
                    executor.getLastReportTime()
            ));
        }
        return candidates;
    }

    /**
     * 将用户选择的三台服务器统一转为正式纳管。
     */
    @Override
    @Transactional
    public TopologyInitializationResponseVO initialize(
            TopologyInitializationRequestDTO request) {

        List<String> hostIps = request.getHostIps();

        // 1. 首次纳管前，平台中不能已经存在正式纳管节点。
        validateNoManagedTopology();

        // 2. 查询三台服务器对应的未纳管在线执行器。
        List<ExecutorInfo> executors = executorInfoService.list(
                new LambdaQueryWrapper<ExecutorInfo>()
                        .eq(ExecutorInfo::getManagementStatus,
                                StatusEnum.INACTIVE.getCode())
                        .eq(ExecutorInfo::getExecutorStatus,
                                StatusEnum.ONLINE.getCode())
                        .in(ExecutorInfo::getHostIp, hostIps)
        );
        if (executors.size()
                != TopologyInitializationConstants.REQUIRED_NODE_COUNT) {
            throw new BusinessException(
                    BusinessCode.OPERATION_ERROR.getCode(),
                    "三台服务器的未纳管在线执行器记录不完整"
            );
        }

        Map<String, ExecutorInfo> executorByHostIp = new HashMap<>();
        Set<String> instanceIds = new HashSet<>();
        for (ExecutorInfo executor : executors) {
            String hostIp = StringUtils.trimToNull(executor.getHostIp());
            String instanceId = StringUtils.trimToNull(
                    executor.getInstanceId()
            );
            if (!isCompleteExecutor(executor)
                    || executorByHostIp.put(hostIp, executor) != null
                    || !instanceIds.add(instanceId)) {
                throw new BusinessException(
                        BusinessCode.OPERATION_ERROR.getCode(),
                        "执行器记录不完整或存在重复绑定"
                );
            }
        }

        // 3. 根据执行器绑定关系查询三条Redis记录。
        List<RedisInstance> redisInstances = redisInstanceService.list(
                new LambdaQueryWrapper<RedisInstance>()
                        .eq(RedisInstance::getManagementStatus,
                                StatusEnum.INACTIVE.getCode())
                        .in(RedisInstance::getInstanceId, instanceIds)
        );
        if (redisInstances.size()
                != TopologyInitializationConstants.REQUIRED_NODE_COUNT) {
            throw new BusinessException(
                    BusinessCode.OPERATION_ERROR.getCode(),
                    "三台服务器的Redis记录不完整"
            );
        }

        Map<String, RedisInstance> redisByInstanceId = new HashMap<>();
        for (RedisInstance redisInstance : redisInstances) {
            String instanceId = StringUtils.trimToNull(
                    redisInstance.getInstanceId()
            );
            if (instanceId == null
                    || redisByInstanceId.put(instanceId, redisInstance)
                    != null) {
                throw new BusinessException(
                        BusinessCode.OPERATION_ERROR.getCode(),
                        "Redis实例记录存在重复绑定"
                );
            }
        }

        // 4. 根据服务器IP查询三条Sentinel记录。
        List<SentinelNodeInfo> sentinelNodes = sentinelNodeInfoService.list(
                new LambdaQueryWrapper<SentinelNodeInfo>()
                        .eq(SentinelNodeInfo::getManagementStatus,
                                StatusEnum.INACTIVE.getCode())
                        .in(SentinelNodeInfo::getNodeIp, hostIps)
        );
        if (sentinelNodes.size()
                != TopologyInitializationConstants.REQUIRED_NODE_COUNT) {
            throw new BusinessException(
                    BusinessCode.OPERATION_ERROR.getCode(),
                    "三台服务器的Sentinel记录不完整"
            );
        }

        Map<String, SentinelNodeInfo> sentinelByHostIp = new HashMap<>();
        for (SentinelNodeInfo sentinelNode : sentinelNodes) {
            String hostIp = StringUtils.trimToNull(sentinelNode.getNodeIp());
            if (hostIp == null
                    || sentinelByHostIp.put(hostIp, sentinelNode) != null) {
                throw new BusinessException(
                        BusinessCode.OPERATION_ERROR.getCode(),
                        "同一服务器IP存在多条Sentinel记录"
                );
            }
        }

        // 5. 按照用户选择的IP逐台检查三类记录和运行状态。
        for (String hostIp : hostIps) {
            ExecutorInfo executor = executorByHostIp.get(hostIp);
            RedisInstance redisInstance = executor == null ? null
                    : redisByInstanceId.get(StringUtils.trimToNull(
                    executor.getInstanceId()
            ));
            SentinelNodeInfo sentinelNode = sentinelByHostIp.get(hostIp);

            if (executor == null
                    || redisInstance == null
                    || sentinelNode == null
                    || !hostIp.equals(redisInstance.getHostIp())
                    || !StatusEnum.RUN_NORMAL.getCode().equals(
                    redisInstance.getRunStatus())
                    || !StatusEnum.ONLINE.getCode().equals(
                    sentinelNode.getSentinelStatus())) {
                throw new BusinessException(
                        BusinessCode.OPERATION_ERROR.getCode(),
                        "服务器的执行器、Redis或Sentinel记录不完整或状态异常："
                                + hostIp
                );
            }
        }

        // 6. 保存六份配置文件摘要，并将三类记录统一改为已纳管。
        LocalDateTime now = LocalDateTime.now();
        for (String hostIp : hostIps) {
            ExecutorInfo executor = executorByHostIp.get(hostIp);
            RedisInstance redisInstance = redisByInstanceId.get(
                    StringUtils.trimToNull(executor.getInstanceId())
            );
            SentinelNodeInfo sentinelNode = sentinelByHostIp.get(hostIp);

            File redisConfigFile = configFileService.getRedisConfigFile(
                    hostIp
            );
            String redisConfigSha256 = configFileService.calculateSha256(
                    redisConfigFile,
                    hostIp + "服务器的Redis"
            );
            redisInstance.setConfigSha256(redisConfigSha256);
            redisInstance.setManagementStatus(StatusEnum.ACTIVE.getCode());
            redisInstance.setUpdatedTime(now);
            if (!redisInstanceService.updateById(redisInstance)) {
                throw new BusinessException(
                        BusinessCode.OPERATION_ERROR.getCode(),
                        "Redis实例纳管失败：" + hostIp
                );
            }

            File sentinelConfigFile = configFileService
                    .getSentinelConfigFile(hostIp);
            String sentinelConfigSha256 = configFileService.calculateSha256(
                    sentinelConfigFile,
                    hostIp + "服务器的Sentinel"
            );
            sentinelNode.setConfigSha256(sentinelConfigSha256);
            sentinelNode.setManagementStatus(StatusEnum.ACTIVE.getCode());
            sentinelNode.setUpdatedTime(now);
            if (!sentinelNodeInfoService.updateById(sentinelNode)) {
                throw new BusinessException(
                        BusinessCode.OPERATION_ERROR.getCode(),
                        "Sentinel节点纳管失败：" + hostIp
                );
            }

            executor.setManagementStatus(StatusEnum.ACTIVE.getCode());
            executor.setUpdatedTime(now);
            if (!executorInfoService.updateById(executor)) {
                throw new BusinessException(
                        BusinessCode.OPERATION_ERROR.getCode(),
                        "执行器纳管失败：" + hostIp
                );
            }
        }

        // 7. 清理拓扑缓存，页面可以立即重新加载正式拓扑。
        clearTopologyCache();

        return new TopologyInitializationResponseVO(
                new ArrayList<>(hostIps),
                TopologyInitializationConstants.SUCCESS_MESSAGE
        );
    }

    /**
     * 检查平台当前是否仍处于首次纳管状态。
     */
    private void validateNoManagedTopology() {
        long redisCount = redisInstanceService.count(
                new LambdaQueryWrapper<RedisInstance>().eq(
                        RedisInstance::getManagementStatus,
                        StatusEnum.ACTIVE.getCode()
                )
        );
        long sentinelCount = sentinelNodeInfoService.count(
                new LambdaQueryWrapper<SentinelNodeInfo>().eq(
                        SentinelNodeInfo::getManagementStatus,
                        StatusEnum.ACTIVE.getCode()
                )
        );
        long executorCount = executorInfoService.count(
                new LambdaQueryWrapper<ExecutorInfo>().eq(
                        ExecutorInfo::getManagementStatus,
                        StatusEnum.ACTIVE.getCode()
                )
        );
        if (redisCount > 0 || sentinelCount > 0 || executorCount > 0) {
            throw new BusinessException(
                    BusinessCode.OPERATION_ERROR.getCode(),
                    "平台已经存在正式纳管拓扑，不能再次执行首次纳管"
            );
        }
    }

    /**
     * 判断执行器是否具备首次纳管需要的完整信息。
     */
    private boolean isCompleteExecutor(ExecutorInfo executor) {
        return executor != null
                && StringUtils.trimToNull(executor.getId()) != null
                && StringUtils.trimToNull(executor.getInstanceId()) != null
                && StringUtils.trimToNull(executor.getHostIp()) != null
                && StringUtils.trimToNull(executor.getExecutorPort()) != null
                && StringUtils.trimToNull(executor.getExecutorToken()) != null
                && StatusEnum.ONLINE.getCode().equals(
                executor.getExecutorStatus())
                && StatusEnum.INACTIVE.getCode().equals(
                executor.getManagementStatus());
    }

    /**
     * 清理Redis和Sentinel拓扑缓存。
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

