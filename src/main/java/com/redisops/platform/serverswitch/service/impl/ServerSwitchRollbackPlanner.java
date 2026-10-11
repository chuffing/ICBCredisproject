package com.redisops.platform.serverswitch.service.impl;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redisops.platform.operation.entity.OperationTask;
import com.redisops.platform.operation.entity.OperationTaskStep;
import com.redisops.platform.operation.model.CreateOperationTaskStepRequest;
import com.redisops.platform.operation.service.OperationTaskRollbackPlanner;
import com.redisops.platform.operation.type.AgentOperationType;
import com.redisops.platform.operation.type.BusinessTaskType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 在新拓扑启动失败后停止新服务，并在需要时恢复旧拓扑 Sentinel。
 */
@Service
@RequiredArgsConstructor
public class ServerSwitchRollbackPlanner implements OperationTaskRollbackPlanner {

    private static final int ROLLBACK_STEP_START = 1001;
    private static final int FIRST_START_STEP = 7;
    private static final int SOURCE_SENTINEL_STOP_STEP = 7;

    private final ObjectMapper objectMapper;

    /** 只处理 SERVER_SWITCH 业务任务。 */
    @Override
    public boolean supports(OperationTask task) {
        return task != null && BusinessTaskType.SERVER_SWITCH.name()
                .equals(task.getTaskType());
    }

    /**
     * 启动阶段失败时停止新拓扑；旧 Sentinel 已进入停止阶段时再把它们启动回来。
     */
    @Override
    public List<CreateOperationTaskStepRequest> buildRollbackSteps(
            OperationTask task, List<OperationTaskStep> currentSteps) {
        int failedStepNo = findFirstFailedForwardStep(currentSteps);
        if (failedStepNo < FIRST_START_STEP) {
            return Collections.emptyList();
        }

        List<NodeContext> targetNodes = readNodes(task.getRequestParams(), "nodes");
        List<CreateOperationTaskStepRequest> result = new ArrayList<>();
        for (NodeContext node : targetNodes) {
            addStep(result, ROLLBACK_STEP_START, node.executorId,
                    AgentOperationType.STOP_SENTINEL);
        }
        for (NodeContext node : targetNodes) {
            addStep(result, ROLLBACK_STEP_START + 1, node.executorId,
                    AgentOperationType.STOP_REDIS);
        }

        if (failedStepNo >= SOURCE_SENTINEL_STOP_STEP) {
            // 第7组并行停止旧 Sentinel；无论其中几台成功，都统一执行幂等启动。
            List<NodeContext> sourceNodes = readNodes(
                    task.getRequestParams(), "sourceNodes");
            for (NodeContext node : sourceNodes) {
                addStep(result, ROLLBACK_STEP_START + 2, node.executorId,
                        AgentOperationType.START_SENTINEL);
            }
        }
        return result;
    }

    /** 找到正常切换步骤中最早失败的阶段。 */
    private int findFirstFailedForwardStep(List<OperationTaskStep> steps) {
        int failedStepNo = Integer.MAX_VALUE;
        for (OperationTaskStep step : steps) {
            if (step.getStepNo() != null
                    && step.getStepNo() < ROLLBACK_STEP_START
                    && "FAILED".equals(step.getStepStatus())) {
                failedStepNo = Math.min(failedStepNo, step.getStepNo());
            }
        }
        if (failedStepNo == Integer.MAX_VALUE) {
            throw new IllegalStateException("服务器切换进入补偿状态但没有失败步骤");
        }
        return failedStepNo;
    }

    /** 从任务审计 JSON 读取指定键下的三台执行器。 */
    private List<NodeContext> readNodes(String requestParams, String key) {
        try {
            Map<String, Object> values = objectMapper.readValue(
                    requestParams, new TypeReference<Map<String, Object>>() { });
            List<NodeContext> nodes = objectMapper.convertValue(
                    values.get(key), new TypeReference<List<NodeContext>>() { });
            if (nodes == null || nodes.size() != 3) {
                throw new IllegalStateException(
                        "服务器切换审计参数" + key + "必须包含3台节点");
            }
            for (NodeContext node : nodes) {
                if (node == null || !StringUtils.hasText(node.executorId)) {
                    throw new IllegalStateException(
                            "服务器切换审计参数" + key + "包含无效节点");
                }
            }
            return nodes;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("服务器切换审计参数无法解析", e);
        }
    }

    /** 添加一条固定不带外部参数的补偿操作。 */
    private void addStep(List<CreateOperationTaskStepRequest> steps,
                         int stepNo,
                         String executorId,
                         AgentOperationType operationType) {
        CreateOperationTaskStepRequest step = new CreateOperationTaskStepRequest();
        step.setStepNo(stepNo);
        step.setExecutorId(executorId);
        step.setOperationType(operationType.name());
        step.setParams(Collections.emptyMap());
        steps.add(step);
    }

    /** 从任务审计 JSON 反序列化的一台服务器。 */
    @Data
    public static class NodeContext {
        private String executorId;
    }
}
