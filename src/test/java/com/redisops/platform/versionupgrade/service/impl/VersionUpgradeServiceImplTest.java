package com.redisops.platform.versionupgrade.service.impl;

import com.redisops.platform.agent.entity.ExecutorInfo;
import com.redisops.platform.agent.service.AgentRegistrationService;
import com.redisops.platform.operation.model.CreateOperationTaskRequest;
import com.redisops.platform.operation.model.CreateOperationTaskStepRequest;
import com.redisops.platform.operation.service.OperationTaskService;
import com.redisops.platform.versionupgrade.entity.RedisVersionPackage;
import com.redisops.platform.versionupgrade.model.VersionUpgradeNodeRequest;
import com.redisops.platform.versionupgrade.model.VersionUpgradeTaskRequest;
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

/** 验证平台生成的三节点版本升级步骤和关键参数。 */
@ExtendWith(MockitoExtension.class)
class VersionUpgradeServiceImplTest {

    @Mock private OperationTaskService operationTaskService;
    @Mock private AgentRegistrationService agentRegistrationService;
    @Mock private VersionPackageService versionPackageService;

    private VersionUpgradeServiceImpl service;

    /** 创建每个测试使用的版本升级服务。 */
    @BeforeEach
    void setUp() {
        service = new VersionUpgradeServiceImpl(
                operationTaskService, agentRegistrationService, versionPackageService);
    }

    /** 应生成七个阶段、十八条操作，并把主从健康检查参数写正确。 */
    @Test
    void shouldBuildCompleteThreeNodeUpgradePlan() {
        RedisVersionPackage versionPackage = new RedisVersionPackage();
        versionPackage.setId(7L);
        versionPackage.setDeclaredVersion("7.2.5");
        versionPackage.setPackageHash(repeat("a", 64));
        versionPackage.setPackageName("redis-7.2.5.tar.gz");
        when(versionPackageService.requireAvailable(7L)).thenReturn(versionPackage);

        ExecutorInfo master = executor("AGENT-A", "172.20.10.12");
        ExecutorInfo replicaOne = executor("AGENT-B", "172.20.10.14");
        ExecutorInfo replicaTwo = executor("AGENT-C", "172.20.10.3");
        when(agentRegistrationService.requireOnlineExecutor("AGENT-A")).thenReturn(master);
        when(agentRegistrationService.requireOnlineExecutor("AGENT-B")).thenReturn(replicaOne);
        when(agentRegistrationService.requireOnlineExecutor("AGENT-C")).thenReturn(replicaTwo);
        when(operationTaskService.createAndDispatch(any())).thenReturn(null);

        service.createAndDispatch(request());

        ArgumentCaptor<CreateOperationTaskRequest> captor =
                ArgumentCaptor.forClass(CreateOperationTaskRequest.class);
        verify(operationTaskService).createAndDispatch(captor.capture());
        CreateOperationTaskRequest created = captor.getValue();
        assertEquals("VERSION_UPGRADE", created.getTaskType());
        assertEquals(18, created.getSteps().size());
        assertEquals(Arrays.asList(1, 2, 3, 4, 5, 6, 7), distinctStepNumbers(created));

        CreateOperationTaskStepRequest masterStart = created.getSteps().stream()
                .filter(step -> step.getStepNo() == 5)
                .findFirst().orElseThrow(AssertionError::new);
        assertEquals("AGENT-A", masterStart.getExecutorId());
        assertEquals("MASTER", masterStart.getParams().get("expectedRole"));

        List<CreateOperationTaskStepRequest> replicaStarts = created.getSteps().stream()
                .filter(step -> step.getStepNo() == 6)
                .collect(Collectors.toList());
        assertEquals(2, replicaStarts.size());
        assertTrue(replicaStarts.stream().allMatch(step ->
                "172.20.10.12".equals(step.getParams().get("expectedMasterHost"))));

        List<CreateOperationTaskStepRequest> sentinelStarts = created.getSteps().stream()
                .filter(step -> step.getStepNo() == 7)
                .collect(Collectors.toList());
        assertEquals(3, sentinelStarts.size());
        assertTrue(sentinelStarts.stream().allMatch(step ->
                "172.20.10.12".equals(step.getParams().get("expectedMasterHost"))
                        && "6379".equals(step.getParams().get("expectedMasterPort"))));
    }

    /** 构造三节点版本升级请求。 */
    private VersionUpgradeTaskRequest request() {
        VersionUpgradeTaskRequest request = new VersionUpgradeTaskRequest();
        request.setVersionPackageId(7L);
        request.setMasterExecutorId("AGENT-A");
        request.setRedisPort(6379);
        request.setCreatedBy("admin");
        request.setOperationReason("test upgrade");
        request.setNodes(Arrays.asList(node("AGENT-A"), node("AGENT-B"), node("AGENT-C")));
        return request;
    }

    /** 创建一个请求节点。 */
    private VersionUpgradeNodeRequest node(String executorId) {
        VersionUpgradeNodeRequest node = new VersionUpgradeNodeRequest();
        node.setExecutorId(executorId);
        return node;
    }

    /** 创建一个已注册执行器。 */
    private ExecutorInfo executor(String executorId, String hostIp) {
        ExecutorInfo executor = new ExecutorInfo();
        executor.setExecutorId(executorId);
        executor.setHostIp(hostIp);
        return executor;
    }

    /** 提取升级计划中按出现顺序排列的不重复阶段编号。 */
    private List<Integer> distinctStepNumbers(CreateOperationTaskRequest request) {
        List<Integer> result = new ArrayList<>();
        for (CreateOperationTaskStepRequest step : request.getSteps()) {
            if (!result.contains(step.getStepNo())) {
                result.add(step.getStepNo());
            }
        }
        return result;
    }

    /** 生成固定长度测试摘要。 */
    private String repeat(String value, int count) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < count; i++) {
            result.append(value);
        }
        return result.toString();
    }
}
