package com.redisops.platform.recovery.service.impl;

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
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 把一次完整拓扑故障恢复拆成执行器能够执行的本机原子操作。
 */
@Service
public class FaultRecoveryServiceImpl implements FaultRecoveryService {

    private static final int REQUIRED_NODE_COUNT = 3;
    private static final int DEFAULT_REDIS_PORT = 6379;
    private final OperationTaskService operationTaskService;
    private final AgentRegistrationService agentRegistrationService;
    private final ExecutorConfigSnapshotService configSnapshotService;

    /** 注入通用任务调度、执行器注册和平台配置快照服务。 */
    public FaultRecoveryServiceImpl(OperationTaskService operationTaskService,
                                    AgentRegistrationService agentRegistrationService,
                                    ExecutorConfigSnapshotService configSnapshotService) {
        this.operationTaskService = operationTaskService;
        this.agentRegistrationService = agentRegistrationService;
        this.configSnapshotService = configSnapshotService;
    }

    /**
     * 校验三节点恢复参数，并生成停止、恢复配置和重新启动拓扑的六个步骤组。
     *
     * @param request 故障恢复业务参数
     * @return 已创建并开始调度的任务详情
     */
    @Override
    public OperationTaskDetail createAndDispatch(FaultRecoveryTaskRequest request) {
        validateBasicRequest(request);

        int redisPort = request.getRedisPort() == null
                ? DEFAULT_REDIS_PORT : request.getRedisPort();
        List<ValidatedNode> nodes = validateNodesAndReadExecutors(request.getNodes());
        ValidatedNode master = findMaster(nodes, request.getMasterExecutorId().trim());

        CreateOperationTaskRequest taskRequest = new CreateOperationTaskRequest();
        taskRequest.setTaskType(BusinessTaskType.FAULT_RECOVERY.name());
        taskRequest.setCreatedBy(request.getCreatedBy().trim());
        taskRequest.setOperationReason(request.getOperationReason().trim());
        taskRequest.setRequestParams(buildAuditParams(nodes, master, redisPort));
        taskRequest.setSteps(buildRecoverySteps(nodes, master, redisPort));

        // 复用通用调度器完成数据库落库、步骤屏障、下发和结果汇总。
        return operationTaskService.createAndDispatch(taskRequest);
    }

    /** 校验任务级必填字段、节点数量和 Redis 端口。 */
    private void validateBasicRequest(FaultRecoveryTaskRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("故障恢复请求不能为空");
        }
        if (!StringUtils.hasText(request.getCreatedBy())) {
            throw new IllegalArgumentException("createdBy 不能为空");
        }
        if (!StringUtils.hasText(request.getOperationReason())) {
            throw new IllegalArgumentException("operationReason 不能为空");
        }
        if (!StringUtils.hasText(request.getMasterExecutorId())) {
            throw new IllegalArgumentException("masterExecutorId 不能为空");
        }
        if (request.getNodes() == null || request.getNodes().size() != REQUIRED_NODE_COUNT) {
            throw new IllegalArgumentException("故障恢复必须且只能包含 3 台服务器");
        }
        if (request.getRedisPort() != null
                && (request.getRedisPort() < 1 || request.getRedisPort() > 65535)) {
            throw new IllegalArgumentException("redisPort 必须在 1 到 65535 之间");
        }
    }

    /** 校验执行器编号唯一性、在线状态，并从平台读取可信配置摘要。 */
    private List<ValidatedNode> validateNodesAndReadExecutors(
            List<FaultRecoveryNodeRequest> requestedNodes) {
        Set<String> executorIds = new HashSet<>();
        List<ValidatedNode> validatedNodes = new ArrayList<>();

        for (FaultRecoveryNodeRequest requestedNode : requestedNodes) {
            if (requestedNode == null || !StringUtils.hasText(requestedNode.getExecutorId())) {
                throw new IllegalArgumentException("每个节点的 executorId 都不能为空");
            }
            String executorId = requestedNode.getExecutorId().trim();
            if (!executorIds.add(executorId)) {
                throw new IllegalArgumentException("executorId 不能重复: " + executorId);
            }

            // 创建任务前先确保三台执行器都在线，避免恢复到一半才发现节点无法连接。
            ExecutorInfo executor = agentRegistrationService.requireOnlineExecutor(executorId);
            // 摘要只能来自平台数据库，调用方不能自行提供或覆盖。
            ExecutorConfigSnapshot snapshot = configSnapshotService
                    .requireActiveSnapshot(executorId);
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

    /**
     * 生成完整拓扑恢复计划。
     * 同一个 stepNo 可以并行下发；下一 stepNo 必须等待上一组全部成功。
     */
    private List<CreateOperationTaskStepRequest> buildRecoverySteps(
            List<ValidatedNode> nodes, ValidatedNode master, int redisPort) {
        List<CreateOperationTaskStepRequest> steps = new ArrayList<>();

        // 第 1、2 组先关闭整个旧拓扑，避免复制和 Sentinel 自动切换干扰配置恢复。
        for (ValidatedNode node : nodes) {
            addStep(steps, 1, node.executor.getExecutorId(),
                    AgentOperationType.STOP_SENTINEL, Collections.emptyMap());
        }
        for (ValidatedNode node : nodes) {
            addStep(steps, 2, node.executor.getExecutorId(),
                    AgentOperationType.STOP_REDIS, Collections.emptyMap());
        }

        // 第 3 组由每台执行器校验本机备份摘要，只有与平台记录一致才恢复配置。
        for (ValidatedNode node : nodes) {
            Map<String, String> restoreParams = new LinkedHashMap<>();
            restoreParams.put("redisConfigSha256", node.redisConfigSha256);
            restoreParams.put("sentinelConfigSha256", node.sentinelConfigSha256);
            addStep(steps, 3, node.executor.getExecutorId(),
                    AgentOperationType.RESTORE_CONFIG, restoreParams);
        }

        // 第 4 组先启动主节点，避免从节点启动时找不到复制源。
        addStep(steps, 4, master.executor.getExecutorId(),
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
            addStep(steps, 5, node.executor.getExecutorId(),
                    AgentOperationType.START_REDIS, replicaParams);
        }

        // 第 6 组启动三台 Sentinel，并确认它们最终都监控恢复后的主节点。
        Map<String, String> sentinelParams = new LinkedHashMap<>();
        sentinelParams.put("expectedMasterHost", master.executor.getHostIp());
        sentinelParams.put("expectedMasterPort", String.valueOf(redisPort));
        for (ValidatedNode node : nodes) {
            addStep(steps, 6, node.executor.getExecutorId(),
                    AgentOperationType.START_SENTINEL, sentinelParams);
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
    private static class ValidatedNode {
        private final ExecutorInfo executor;
        private final String redisConfigSha256;
        private final String sentinelConfigSha256;

        /** 保存后续编排需要的执行器和两个配置摘要。 */
        private ValidatedNode(ExecutorInfo executor,
                              String redisConfigSha256,
                              String sentinelConfigSha256) {
            this.executor = executor;
            this.redisConfigSha256 = redisConfigSha256;
            this.sentinelConfigSha256 = sentinelConfigSha256;
        }
    }
}
