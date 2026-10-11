package com.redisops.platform.recovery.service.impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import com.redisops.platform.operation.model.CreateOperationTaskRequest;
import com.redisops.platform.operation.model.CreateOperationTaskStepRequest;
import com.redisops.platform.recovery.model.FaultRecoveryNodeRequest;
import com.redisops.platform.recovery.model.FaultRecoveryTaskRequest;
import com.redisops.platform.operation.model.OperationTaskDetail;
import com.redisops.platform.agent.configsnapshot.entity.ExecutorConfigSnapshot;
import com.redisops.platform.agent.entity.ExecutorInfo;
import com.redisops.platform.operation.type.AgentOperationType;
import com.redisops.platform.operation.type.BusinessTaskType;
import com.redisops.platform.agent.service.AgentRegistrationService;
import com.redisops.platform.agent.configsnapshot.service.ExecutorConfigSnapshotService;
import com.redisops.platform.recovery.service.FaultRecoveryService;
import com.redisops.platform.operation.service.OperationTaskService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 把一次完整拓扑故障恢复拆成执行器能够执行的本机原子操作。
 */
@Service
@RequiredArgsConstructor
public class FaultRecoveryServiceImpl implements FaultRecoveryService {

    private static final int DEFAULT_REDIS_PORT = 6379;
    private final OperationTaskService operationTaskService;
    private final AgentRegistrationService agentRegistrationService;
    private final ExecutorConfigSnapshotService configSnapshotService;

    /**
     * 校验三节点恢复参数，并生成停止、恢复配置和重新启动拓扑的六个步骤组。
     *
     * @param request 故障恢复业务参数
     * @return 已创建并开始调度的任务详情
     */
    @Override
    public OperationTaskDetail createAndDispatch(FaultRecoveryTaskRequest request) {

        // 1. 确定Redis端口。
        int redisPort = request.getRedisPort() == null
                ? DEFAULT_REDIS_PORT
                : request.getRedisPort();
        // 2. 查询三台执行器，并获取配置快照。
        List<ValidatedNode> nodes = loadValidatedNodes(request.getNodes());

        // 3. 确定故障恢复的主节点。
        ValidatedNode master = findMaster(nodes, request.getMasterExecutorId().trim());

        // 4. 创建请求对象。
        CreateOperationTaskRequest taskRequest = new CreateOperationTaskRequest();
        // 设置任务类型
        taskRequest.setTaskType(BusinessTaskType.FAULT_RECOVERY.name());
        // 设置创建人
        taskRequest.setCreatedBy(request.getCreatedBy().trim());
        // 设置操作原因
        taskRequest.setOperationReason(request.getOperationReason().trim());
        // 生成审计参数
        taskRequest.setRequestParams(buildAuditParams(nodes, master, redisPort));
        // 生成故障恢复步骤
        taskRequest.setSteps(buildRecoverySteps(nodes, master, redisPort));

        // 5. 将组装好的故障恢复任务交给公共运维任务模块，由它负责保存数据库、调度步骤和下发任务。
        return operationTaskService.createAndDispatch(taskRequest);
    }

    /** 。 */
    private List<ValidatedNode> loadValidatedNodes(
            List<FaultRecoveryNodeRequest> requestedNodes) {

        // 创建一个空列表用来保存经过处理之后的节点对象。
        List<ValidatedNode> validatedNodes = new ArrayList<>();

        for (FaultRecoveryNodeRequest requestedNode : requestedNodes) {
            // 获取执行器编号并去除首尾空格。
            String executorId = requestedNode.getExecutorId().trim();

            // 查询编号为executorId的执行器是否启用、在线状态。
            ExecutorInfo executor = agentRegistrationService.requireOnlineExecutor(executorId);

            // 获取执行器的配置快照。
            ExecutorConfigSnapshot snapshot = configSnapshotService
                    .requireActiveSnapshot(executorId);
            // 组装validatedNode对象
            validatedNodes.add(new ValidatedNode(
                    executor,
                    snapshot.getRedisConfigSha256(),
                    snapshot.getSentinelConfigSha256()));
        }
        return validatedNodes;
    }

    /** 找到备份拓扑中唯一指定的主 Redis 节点。 */
    private ValidatedNode findMaster(List<ValidatedNode> nodes, String masterExecutorId) {
        for (ValidatedNode node : nodes) {
            if (masterExecutorId.equals(node.executor.getExecutorId())) {
                return node;
            }
        }
        throw new IllegalArgumentException("masterExecutorId 必须属于本次恢复的 3 台服务器");
    }

    /** 生成保存到任务主记录中的审计参数。 */
    private Map<String, Object> buildAuditParams(List<ValidatedNode> nodes,
                                                  ValidatedNode master,
                                                  int redisPort) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("topologyType", "THREE_NODE_REDIS_SENTINEL");
        params.put("masterExecutorId", master.executor.getExecutorId());
        params.put("masterHost", master.executor.getHostIp());
        params.put("redisPort", redisPort);

        List<Map<String, String>> nodeParams = new ArrayList<>();
        for (ValidatedNode node : nodes) {
            Map<String, String> item = new LinkedHashMap<>();
            item.put("executorId", node.executor.getExecutorId());
            item.put("hostIp", node.executor.getHostIp());
            item.put("redisConfigSha256", node.redisConfigSha256);
            item.put("sentinelConfigSha256", node.sentinelConfigSha256);
            nodeParams.add(item);
        }
        params.put("nodes", nodeParams);
        return params;
    }

    /** 根据恢复拓扑生成六组运维步骤，并为每条步骤指定目标执行器。 */
    private List<CreateOperationTaskStepRequest> buildRecoverySteps(
            List<ValidatedNode> nodes, ValidatedNode master, int redisPort) {

        // 创建一个空列表steps，用于保存所有待执行的操作。
        List<CreateOperationTaskStepRequest> steps = new ArrayList<>();

        // 第 1 组，关闭三台sentinel。
        for (ValidatedNode node : nodes) {
            addStep(steps, // 保存步骤的列表
                    1, // 步骤组编号
                    node.executor.getExecutorId(), // 执行该操作的执行器
                    AgentOperationType.STOP_SENTINEL, // 需要执行的操作类型
                    Collections.emptyMap()); // 该操作没有额外参数
        }

        // 第 2 组，关闭三台redis。
        for (ValidatedNode node : nodes) {
            addStep(steps,
                    2,
                    node.executor.getExecutorId(),
                    AgentOperationType.STOP_REDIS,
                    Collections.emptyMap());
        }

        // 第 3 组由每台执行器校验本机备份摘要，只有与平台记录一致才恢复配置。
        for (ValidatedNode node : nodes) {
            Map<String, String> restoreParams = new LinkedHashMap<>();
            restoreParams.put("redisConfigSha256", node.redisConfigSha256);
            restoreParams.put("sentinelConfigSha256", node.sentinelConfigSha256);
            addStep(steps,
                    3,
                    node.executor.getExecutorId(),
                    AgentOperationType.RESTORE_CONFIG,
                    restoreParams);
        }

        // 第 4 组先启动主节点，避免从节点启动时找不到复制源。
        addStep(steps,
                4,
                master.executor.getExecutorId(),
                AgentOperationType.START_REDIS,
                singleParam("expectedRole", "MASTER"));

        // 第 5 组并行启动两个从节点，并验证它们都指向刚启动的主节点。
        for (ValidatedNode node : nodes) {
            if (node == master) {
                continue;
            }
            Map<String, String> replicaParams = new LinkedHashMap<>();
            replicaParams.put("expectedRole", "REPLICA");
            replicaParams.put("expectedMasterHost", master.executor.getHostIp());
            replicaParams.put("expectedMasterPort", String.valueOf(redisPort));
            addStep(steps,
                    5,
                    node.executor.getExecutorId(),
                    AgentOperationType.START_REDIS,
                    replicaParams);
        }

        // 第 6 组启动三台 Sentinel，并确认它们最终都监控恢复后的主节点。
        Map<String, String> sentinelParams = new LinkedHashMap<>();
        sentinelParams.put("expectedMasterHost", master.executor.getHostIp());
        sentinelParams.put("expectedMasterPort", String.valueOf(redisPort));
        for (ValidatedNode node : nodes) {
            addStep(steps,
                    6,
                    node.executor.getExecutorId(),
                    AgentOperationType.START_SENTINEL,
                    sentinelParams);
        }
        return steps;
    }

    /** 创建只有一个键值的操作参数。 */
    private Map<String, String> singleParam(String key, String value) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put(key, value);
        return params;
    }

    /** 向通用执行计划中加入一台执行器上的一个原子操作。 */
    private void addStep(List<CreateOperationTaskStepRequest> steps,
                         int stepNo,
                         String executorId,
                         AgentOperationType operationType,
                         Map<String, String> params) {
        CreateOperationTaskStepRequest step = new CreateOperationTaskStepRequest();
        step.setStepNo(stepNo);
        step.setExecutorId(executorId);
        step.setOperationType(operationType.name());
        step.setParams(params);
        steps.add(step);
    }

    /** 已完成参数校验并绑定数据库执行器记录的内部节点模型。 */
    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    private static class ValidatedNode {
        private final ExecutorInfo executor;
        private final String redisConfigSha256;
        private final String sentinelConfigSha256;
    }
}
