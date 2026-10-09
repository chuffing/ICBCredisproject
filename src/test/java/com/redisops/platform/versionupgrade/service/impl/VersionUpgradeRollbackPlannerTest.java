package com.redisops.platform.versionupgrade.service.impl;

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
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 验证不同失败阶段生成的版本升级补偿计划。 */
class VersionUpgradeRollbackPlannerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final VersionUpgradeRollbackPlanner planner =
            new VersionUpgradeRollbackPlanner(objectMapper);

    /** 部署包阶段失败时线上服务未受影响，不应生成补偿步骤。 */
    @Test
    void shouldNotRollbackWhenPackageDeploymentFails() throws Exception {
        List<OperationTaskStep> steps = Arrays.asList(
                step(1, "AGENT-A", "DEPLOY_VERSION", "FAILED"));
        assertTrue(planner.buildRollbackSteps(task(), steps).isEmpty());
    }

    /** 切换阶段部分成功时只回滚成功节点，随后恢复完整旧拓扑。 */
    @Test
    void shouldRollbackOnlySuccessfullySwitchedNodes() throws Exception {
        List<OperationTaskStep> steps = new ArrayList<>();
        steps.add(step(4, "AGENT-A", "SWITCH_VERSION", "SUCCESS"));
        steps.add(step(4, "AGENT-B", "SWITCH_VERSION", "FAILED"));
        steps.add(step(4, "AGENT-C", "SWITCH_VERSION", "SUCCESS"));

        List<CreateOperationTaskStepRequest> rollback =
                planner.buildRollbackSteps(task(), steps);
        List<CreateOperationTaskStepRequest> linkRollbacks = rollback.stream()
                .filter(step -> "ROLLBACK_VERSION".equals(step.getOperationType()))
                .collect(Collectors.toList());
        assertEquals(2, linkRollbacks.size());
        assertEquals(Arrays.asList("AGENT-A", "AGENT-C"), linkRollbacks.stream()
                .map(CreateOperationTaskStepRequest::getExecutorId)
                .collect(Collectors.toList()));
        assertTrue(rollback.stream().anyMatch(step -> step.getStepNo() == 1002
                && "AGENT-A".equals(step.getExecutorId())
                && "START_REDIS".equals(step.getOperationType())));
    }

    /** 构造包含三节点拓扑审计参数的版本升级任务。 */
    private OperationTask task() throws Exception {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("masterExecutorId", "AGENT-A");
        params.put("masterHost", "172.20.10.12");
        params.put("redisPort", 6379);
        List<Map<String, String>> nodes = new ArrayList<>();
        nodes.add(node("AGENT-A"));
        nodes.add(node("AGENT-B"));
        nodes.add(node("AGENT-C"));
        params.put("nodes", nodes);

        OperationTask task = new OperationTask();
        task.setTaskNo("TASK202610080001");
        task.setTaskType("VERSION_UPGRADE");
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
