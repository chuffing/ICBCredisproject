package com.danuvius.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.danuvius.platform.common.constant.CommonConstant;
import com.danuvius.platform.common.constant.OperationTaskConstants;
import com.danuvius.platform.common.constant.ValidationConstants;
import com.danuvius.platform.common.exception.BusinessCode;
import com.danuvius.platform.common.exception.BusinessException;
import com.danuvius.platform.model.dto.operation.CurrentTopologyNode;
import com.danuvius.platform.model.entity.ExecutorInfo;
import com.danuvius.platform.model.entity.RedisInstance;
import com.danuvius.platform.model.entity.SentinelNodeInfo;
import com.danuvius.platform.model.enums.instance.ManagementStatusEnum;
import com.danuvius.platform.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 查询故障恢复和版本升级共同使用的当前生产拓扑。
 */
@Service
@RequiredArgsConstructor
public class CurrentTopologyService {

    private final RedisInstanceService redisInstanceService;

    private final SentinelNodeInfoService sentinelNodeInfoService;

    private final ExecutorInfoService executorInfoService;

    /**
     * 查询并组合当前正式纳管的三台Redis、Sentinel和执行器。
     */
    public List<CurrentTopologyNode> loadCurrentNodes() {

        // 1. 查询当前正式纳管的三台Redis实例。
        List<RedisInstance> redisInstances = redisInstanceService.list(
                new LambdaQueryWrapper<RedisInstance>().eq(
                        RedisInstance::getManagementStatus,
                        ManagementStatusEnum.ACTIVE.getCode()
                )
        );
        if (redisInstances.size()
                != OperationTaskConstants.CURRENT_TOPOLOGY_NODE_COUNT) {
            throw new BusinessException(
                    BusinessCode.OPERATION_ERROR.getCode(),
                    "当前环境中必须正好存在3台已纳管Redis实例"
            );
        }

        // 2. 查询当前正式纳管的三台Sentinel节点。
        List<SentinelNodeInfo> sentinelNodes = sentinelNodeInfoService.list(
                new LambdaQueryWrapper<SentinelNodeInfo>().eq(
                        SentinelNodeInfo::getManagementStatus,
                        ManagementStatusEnum.ACTIVE.getCode()
                )
        );
        if (sentinelNodes.size()
                != OperationTaskConstants.CURRENT_TOPOLOGY_NODE_COUNT) {
            throw new BusinessException(
                    BusinessCode.OPERATION_ERROR.getCode(),
                    "当前环境中必须正好存在3台已纳管Sentinel节点"
            );
        }

        List<String> instanceIds = new ArrayList<>();
        for (RedisInstance redisInstance : redisInstances) {
            instanceIds.add(redisInstance.getInstanceId());
        }

        // 3. 根据Redis实例编号查询已经绑定的三台执行器。
        List<ExecutorInfo> executors = executorInfoService.list(
                new LambdaQueryWrapper<ExecutorInfo>()
                        .eq(ExecutorInfo::getManagementStatus,
                                ManagementStatusEnum.ACTIVE.getCode())
                        .in(ExecutorInfo::getInstanceId, instanceIds)
        );
        if (executors.size()
                != OperationTaskConstants.CURRENT_TOPOLOGY_NODE_COUNT) {
            throw new BusinessException(
                    BusinessCode.OPERATION_ERROR.getCode(),
                    "当前Redis实例绑定的已纳管执行器记录不完整"
            );
        }

        // 4. 按服务器IP建立Sentinel索引。
        Map<String, SentinelNodeInfo> sentinelByHost = new HashMap<>();
        for (SentinelNodeInfo sentinelNode : sentinelNodes) {
            String hostIp = StringUtils.trimToNull(sentinelNode.getNodeIp());
            if (hostIp == null
                    || sentinelByHost.put(hostIp, sentinelNode) != null) {
                throw new BusinessException(
                        BusinessCode.OPERATION_ERROR.getCode(),
                        "已纳管Sentinel节点的服务器IP为空或重复"
                );
            }
        }

        // 5. 按redis实例编号建立执行器索引。
        Map<String, ExecutorInfo> executorByInstanceId = new HashMap<>();
        for (ExecutorInfo executor : executors) {
            executorByInstanceId.put(executor.getInstanceId(), executor);
        }

        // 6. 提取后续运维步骤实际需要的执行器编号和配置摘要。
        List<CurrentTopologyNode> nodes = new ArrayList<>();
        Set<String> redisHosts = new HashSet<>();
        for (RedisInstance redisInstance : redisInstances) {
            String hostIp = StringUtils.trimToNull(redisInstance.getHostIp());
            if (hostIp == null || !redisHosts.add(hostIp)) {
                throw new BusinessException(
                        BusinessCode.OPERATION_ERROR.getCode(),
                        "已纳管Redis实例的服务器IP为空或重复"
                );
            }

            SentinelNodeInfo sentinelNode = sentinelByHost.remove(hostIp);
            ExecutorInfo executor = executorByInstanceId.get(
                    redisInstance.getInstanceId()
            );
            if (sentinelNode == null || executor == null) {
                throw new BusinessException(
                        BusinessCode.OPERATION_ERROR.getCode(),
                        "Redis实例缺少Sentinel或已绑定执行器：" + hostIp
                );
            }

            validateExecutor(executor, hostIp);
            validateConfigSha256(
                    redisInstance.getConfigSha256(),
                    "Redis",
                    hostIp
            );
            validateConfigSha256(
                    sentinelNode.getConfigSha256(),
                    "Sentinel",
                    hostIp
            );

            nodes.add(new CurrentTopologyNode(
                    executor.getId(),
                    redisInstance.getConfigSha256(),
                    sentinelNode.getConfigSha256()
            ));
        }

        if (!sentinelByHost.isEmpty()) {
            throw new BusinessException(
                    BusinessCode.OPERATION_ERROR.getCode(),
                    "存在未匹配Redis实例的已纳管Sentinel节点"
            );
        }

        return nodes;
    }

    /**
     * 检查执行器编号和在线状态。
     */
    private void validateExecutor(ExecutorInfo executor, String hostIp) {
        if (StringUtils.trimToNull(executor.getId()) == null
                || !CommonConstant.ONLINE
                .equals(executor.getExecutorStatus())) {
            throw new BusinessException(
                    BusinessCode.OPERATION_ERROR.getCode(),
                    "服务器上的执行器编号为空或当前不在线：" + hostIp
            );
        }
    }

    /**
     * 检查备份配置文件的SHA256摘要。
     */
    private void validateConfigSha256(
            String sha256,
            String configType,
            String hostIp) {

        String value = StringUtils.trimToNull(sha256);
        if (value == null
                || !ValidationConstants.SHA256_PATTERN
                .matcher(value)
                .matches()) {
            throw new BusinessException(
                    BusinessCode.OPERATION_ERROR.getCode(),
                    hostIp + "服务器的" + configType
                            + "备份配置SHA256不正确"
            );
        }
    }
}
