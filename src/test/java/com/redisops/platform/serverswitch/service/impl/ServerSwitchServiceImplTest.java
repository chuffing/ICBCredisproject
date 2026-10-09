package com.redisops.platform.serverswitch.service.impl;

import com.redisops.platform.agent.configsnapshot.entity.ExecutorConfigSnapshot;
import com.redisops.platform.agent.configsnapshot.service.ExecutorConfigSnapshotService;
import com.redisops.platform.agent.entity.ExecutorInfo;
import com.redisops.platform.agent.service.AgentRegistrationService;
import com.redisops.platform.operation.model.CreateOperationTaskRequest;
import com.redisops.platform.operation.model.CreateOperationTaskStepRequest;
import com.redisops.platform.operation.service.OperationTaskService;
import com.redisops.platform.serverswitch.model.ServerSwitchNodeRequest;
import com.redisops.platform.serverswitch.model.ServerSwitchTaskRequest;
import com.redisops.platform.versionupgrade.entity.RedisVersionPackage;
import com.redisops.platform.versionupgrade.service.VersionPackageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 验证平台生成的三节点服务器切换步骤、配置摘要和主从参数。 */
@ExtendWith(MockitoExtension.class)
class ServerSwitchServiceImplTest {

    @Mock private OperationTaskService operationTaskService;
    @Mock private AgentRegistrationService agentRegistrationService;
    @Mock private VersionPackageService versionPackageService;
    @Mock private ExecutorConfigSnapshotService configSnapshotService;

    private ServerSwitchServiceImpl service;

    /** 创建每个测试使用的服务器切换服务。 */
    @BeforeEach
    void setUp() {
        service = new ServerSwitchServiceImpl(
                operationTaskService,
                agentRegistrationService,
                versionPackageService,
                configSnapshotService);
    }

    /** 应生成十阶段二十七条操作，并在启动新 Sentinel 前停止旧 Sentinel。 */
    @Test
    void shouldBuildCompleteThreeNodeServerSwitchPlan() {
        RedisVersionPackage versionPackage = new RedisVersionPackage();
        versionPackage.setId(8L);
        versionPackage.setDeclaredVersion("7.2.5");
        versionPackage.setPackageHash(repeat("a", 64));
        versionPackage.setPackageName("redis-7.2.5.tar.gz");
        when(versionPackageService.requireAvailable(8L)).thenReturn(versionPackage);

        prepareNode("AGENT-N1", "10.0.0.11", "1", "4");
        prepareNode("AGENT-N2", "10.0.0.12", "2", "5");
        prepareNode("AGENT-N3", "10.0.0.13", "3", "6");
        prepareSourceNode("AGENT-O1", "10.0.0.21");
        prepareSourceNode("AGENT-O2", "10.0.0.22");
        prepareSourceNode("AGENT-O3", "10.0.0.23");
        when(operationTaskService.createAndDispatch(any())).thenReturn(null);

        service.createAndDispatch(request());

        ArgumentCaptor<CreateOperationTaskRequest> captor =
                ArgumentCaptor.forClass(CreateOperationTaskRequest.class);
        verify(operationTaskService).createAndDispatch(captor.capture());
        CreateOperationTaskRequest created = captor.getValue();
        assertEquals("SERVER_SWITCH", created.getTaskType());
        assertEquals(27, created.getSteps().size());
        assertEquals(Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10),
                distinctStepNumbers(created));

        List<CreateOperationTaskStepRequest> configSteps = created.getSteps().stream()
                .filter(step -> step.getStepNo() == 5)
                .collect(Collectors.toList());
        assertEquals(3, configSteps.size());
        assertEquals(repeat("1", 64), configSteps.get(0).getParams()
                .get("redisConfigSha256"));

        CreateOperationTaskStepRequest masterStart = created.getSteps().stream()
                .filter(step -> step.getStepNo() == 8)
                .findFirst().orElseThrow(AssertionError::new);
        assertEquals("AGENT-N1", masterStart.getExecutorId());
        assertEquals("MASTER", masterStart.getParams().get("expectedRole"));

        List<CreateOperationTaskStepRequest> replicaStarts = created.getSteps().stream()
                .filter(step -> step.getStepNo() == 9)
                .collect(Collectors.toList());
        assertEquals(2, replicaStarts.size());
        assertTrue(replicaStarts.stream().allMatch(step ->
                "10.0.0.11".equals(step.getParams().get("expectedMasterHost"))));

        List<CreateOperationTaskStepRequest> sourceStops = created.getSteps().stream()
                .filter(step -> step.getStepNo() == 7)
                .collect(Collectors.toList());
        assertEquals(3, sourceStops.size());
        assertTrue(sourceStops.stream().allMatch(step ->
                "STOP_SENTINEL".equals(step.getOperationType())));

        List<CreateOperationTaskStepRequest> sentinelStarts = created.getSteps().stream()
                .filter(step -> step.getStepNo() == 10)
                .collect(Collectors.toList());
        assertEquals(3, sentinelStarts.size());
        assertTrue(sentinelStarts.stream().allMatch(step ->
                "10.0.0.11".equals(step.getParams().get("expectedMasterHost"))
                        && "6379".equals(step.getParams().get("expectedMasterPort"))));
    }

    /** 准备一个在线执行器及其平台可信配置摘要。 */
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

    /** 准备一台只需要在线状态的旧拓扑执行器。 */
    private void prepareSourceNode(String executorId, String hostIp) {
        ExecutorInfo executor = new ExecutorInfo();
        executor.setExecutorId(executorId);
        executor.setHostIp(hostIp);
        when(agentRegistrationService.requireOnlineExecutor(executorId))
                .thenReturn(executor);
    }

    /** 构造三台新服务器和三台旧服务器的切换请求。 */
    private ServerSwitchTaskRequest request() {
        ServerSwitchTaskRequest request = new ServerSwitchTaskRequest();
        request.setVersionPackageId(8L);
        request.setMasterExecutorId("AGENT-N1");
        request.setRedisPort(6379);
        request.setCreatedBy("admin");
        request.setOperationReason("replace three servers");
        request.setNodes(Arrays.asList(
                node("AGENT-N1"), node("AGENT-N2"), node("AGENT-N3")));
        request.setSourceNodes(Arrays.asList(
                node("AGENT-O1"), node("AGENT-O2"), node("AGENT-O3")));
        return request;
    }

    /** 创建请求中的一台新服务器。 */
    private ServerSwitchNodeRequest node(String executorId) {
        ServerSwitchNodeRequest node = new ServerSwitchNodeRequest();
        node.setExecutorId(executorId);
        return node;
    }

    /** 提取计划中按出现顺序排列的不重复阶段编号。 */
    private List<Integer> distinctStepNumbers(CreateOperationTaskRequest request) {
        List<Integer> result = new ArrayList<>();
        for (CreateOperationTaskStepRequest step : request.getSteps()) {
            if (!result.contains(step.getStepNo())) {
                result.add(step.getStepNo());
            }
        }
        return result;
    }

    /** 生成测试使用的固定长度摘要。 */
    private String repeat(String value, int count) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < count; i++) {
            result.append(value);
        }
        return result.toString();
    }
}
