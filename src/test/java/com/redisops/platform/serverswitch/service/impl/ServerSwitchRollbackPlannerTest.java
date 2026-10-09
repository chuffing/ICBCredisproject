package com.redisops.platform.serverswitch.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redisops.platform.operation.entity.OperationTask;
import com.redisops.platform.operation.entity.OperationTaskStep;
import com.redisops.platform.operation.model.CreateOperationTaskStepRequest;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 验证服务器切换在不同失败阶段生成的安全补偿计划。 */
class ServerSwitchRollbackPlannerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ServerSwitchRollbackPlanner planner =
            new ServerSwitchRollbackPlanner(objectMapper);

    /** 服务启动前失败时新拓扑仍处于停止状态，不需要重复补偿。 */
    @Test
    void shouldNotCompensateBeforeStartStage() throws Exception {
        List<OperationTaskStep> steps = Arrays.asList(
                step(5, "AGENT-N1", "DEPLOY_CONFIG", "FAILED"));
        assertTrue(planner.buildRollbackSteps(task(), steps).isEmpty());
    }

    /** 任一启动阶段失败时应先停全部Sentinel，再停全部Redis。 */
    @Test
    void shouldStopEntireNewTopologyWhenStartFails() throws Exception {
        List<OperationTaskStep> steps = Arrays.asList(
                step(8, "AGENT-N2", "START_REDIS", "FAILED"));

        List<CreateOperationTaskStepRequest> rollback =
                planner.buildRollbackSteps(task(), steps);
        assertEquals(9, rollback.size());
        assertEquals(3, rollback.stream()
                .filter(item -> item.getStepNo() == 1001
                        && "STOP_SENTINEL".equals(item.getOperationType()))
                .count());
        assertEquals(3, rollback.stream()
                .filter(item -> item.getStepNo() == 1002
                        && "STOP_REDIS".equals(item.getOperationType()))
                .count());
        assertEquals(3, rollback.stream()
                .filter(item -> item.getStepNo() == 1003
                        && "START_SENTINEL".equals(item.getOperationType()))
                .count());
    }

    /** 旧 Sentinel 停止后失败时还应把三台旧 Sentinel 启动回来。 */
    @Test
    void shouldRestoreSourceSentinelsWhenCutoverFails() throws Exception {
        List<OperationTaskStep> steps = Arrays.asList(
                step(10, "AGENT-N1", "START_SENTINEL", "FAILED"));

        List<CreateOperationTaskStepRequest> rollback =
                planner.buildRollbackSteps(task(), steps);
        assertEquals(9, rollback.size());
        assertEquals(3, rollback.stream()
                .filter(item -> item.getStepNo() == 1003
                        && "START_SENTINEL".equals(item.getOperationType()))
                .count());
    }

    /** 构造包含新旧各三台服务器审计参数的任务。 */
    private OperationTask task() throws Exception {
        Map<String, Object> params = new LinkedHashMap<>();
        List<Map<String, String>> nodes = new ArrayList<>();
        nodes.add(node("AGENT-N1"));
        nodes.add(node("AGENT-N2"));
        nodes.add(node("AGENT-N3"));
        params.put("nodes", nodes);
        List<Map<String, String>> sourceNodes = new ArrayList<>();
        sourceNodes.add(node("AGENT-O1"));
        sourceNodes.add(node("AGENT-O2"));
        sourceNodes.add(node("AGENT-O3"));
        params.put("sourceNodes", sourceNodes);

        OperationTask task = new OperationTask();
        task.setTaskNo("TASK-SERVER-SWITCH-1");
        task.setTaskType("SERVER_SWITCH");
        task.setRequestParams(objectMapper.writeValueAsString(params));
        return task;
    }

    /** 创建审计参数中的节点。 */
    private Map<String, String> node(String executorId) {
        Map<String, String> node = new LinkedHashMap<>();
        node.put("executorId", executorId);
        return node;
    }

    /** 创建一条指定状态的任务步骤。 */
    private OperationTaskStep step(int stepNo, String executorId,
                                   String operationType, String status) {
        OperationTaskStep step = new OperationTaskStep();
        step.setStepNo(stepNo);
        step.setExecutorId(executorId);
        step.setOperationType(operationType);
        step.setStepStatus(status);
        return step;
    }
}
