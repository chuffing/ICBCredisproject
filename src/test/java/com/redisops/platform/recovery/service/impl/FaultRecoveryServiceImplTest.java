package com.redisops.platform.recovery.service.impl;

import com.redisops.platform.agent.configsnapshot.entity.ExecutorConfigSnapshot;
import com.redisops.platform.agent.configsnapshot.service.ExecutorConfigSnapshotService;
import com.redisops.platform.agent.entity.ExecutorInfo;
import com.redisops.platform.agent.service.AgentRegistrationService;
import com.redisops.platform.operation.model.CreateOperationTaskRequest;
import com.redisops.platform.operation.model.CreateOperationTaskStepRequest;
import com.redisops.platform.operation.service.OperationTaskService;
import com.redisops.platform.recovery.model.FaultRecoveryNodeRequest;
import com.redisops.platform.recovery.model.FaultRecoveryTaskRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 验证故障恢复生成完整步骤并校验 Redis 与 Sentinel 的目标主库。 */
@ExtendWith(MockitoExtension.class)
class FaultRecoveryServiceImplTest {

    @Mock private OperationTaskService operationTaskService;
    @Mock private AgentRegistrationService agentRegistrationService;
    @Mock private ExecutorConfigSnapshotService configSnapshotService;

    private FaultRecoveryServiceImpl service;

    /** 创建每个测试使用的故障恢复服务。 */
    @BeforeEach
    void setUp() {
        service = new FaultRecoveryServiceImpl(
                operationTaskService, agentRegistrationService, configSnapshotService);
    }

    /** 应生成十五条操作，并让副本和 Sentinel 都指向指定主节点。 */
    @Test
    void shouldBuildCompleteRecoveryPlan() {
        prepareNode("AGENT-A", "172.20.10.5", "a", "d");
        prepareNode("AGENT-B", "172.20.10.4", "b", "e");
        prepareNode("AGENT-C", "172.20.10.6", "c", "f");
        when(operationTaskService.createAndDispatch(any())).thenReturn(null);

        service.createAndDispatch(request());

        ArgumentCaptor<CreateOperationTaskRequest> captor =
                ArgumentCaptor.forClass(CreateOperationTaskRequest.class);
        verify(operationTaskService).createAndDispatch(captor.capture());
        CreateOperationTaskRequest created = captor.getValue();
        assertEquals("FAULT_RECOVERY", created.getTaskType());
        assertEquals(15, created.getSteps().size());

        List<CreateOperationTaskStepRequest> replicaStarts = created.getSteps().stream()
                .filter(step -> step.getStepNo() == 5)
                .collect(Collectors.toList());
        assertEquals(2, replicaStarts.size());
        assertTrue(replicaStarts.stream().allMatch(step ->
                "172.20.10.5".equals(step.getParams().get("expectedMasterHost"))));

        List<CreateOperationTaskStepRequest> sentinelStarts = created.getSteps().stream()
                .filter(step -> step.getStepNo() == 6)
                .collect(Collectors.toList());
        assertEquals(3, sentinelStarts.size());
        assertTrue(sentinelStarts.stream().allMatch(step ->
                "172.20.10.5".equals(step.getParams().get("expectedMasterHost"))
                        && "6379".equals(step.getParams().get("expectedMasterPort"))));
    }

    /** 准备在线执行器及其平台配置摘要。 */
    private void prepareNode(String executorId, String hostIp,
                             String redisHashCharacter,
                             String sentinelHashCharacter) {
        ExecutorInfo executor = new ExecutorInfo();
        executor.setExecutorId(executorId);
        executor.setHostIp(hostIp);
        when(agentRegistrationService.requireOnlineExecutor(executorId))
                .thenReturn(executor);

        ExecutorConfigSnapshot snapshot = new ExecutorConfigSnapshot();
        snapshot.setExecutorId(executorId);
        snapshot.setRedisConfigSha256(repeat(redisHashCharacter, 64));
        snapshot.setSentinelConfigSha256(repeat(sentinelHashCharacter, 64));
        when(configSnapshotService.requireActiveSnapshot(executorId))
                .thenReturn(snapshot);
    }

    /** 构造三节点故障恢复请求。 */
    private FaultRecoveryTaskRequest request() {
        FaultRecoveryTaskRequest request = new FaultRecoveryTaskRequest();
        request.setCreatedBy("admin");
        request.setOperationReason("test recovery");
        request.setMasterExecutorId("AGENT-A");
        request.setRedisPort(6379);
        request.setNodes(Arrays.asList(
                node("AGENT-A"), node("AGENT-B"), node("AGENT-C")));
        return request;
    }

    /** 创建请求中的一台节点。 */
    private FaultRecoveryNodeRequest node(String executorId) {
        FaultRecoveryNodeRequest node = new FaultRecoveryNodeRequest();
        node.setExecutorId(executorId);
        return node;
    }

    /** 生成固定长度测试摘要。 */
    private String repeat(String value, int count) {
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < count; index++) {
            result.append(value);
        }
        return result.toString();
    }
}
