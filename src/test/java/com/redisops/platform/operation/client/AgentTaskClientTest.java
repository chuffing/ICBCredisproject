package com.redisops.platform.operation.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redisops.platform.agent.configsnapshot.model.ConfigSnapshotFiles;
import com.redisops.platform.agent.configsnapshot.service.ExecutorConfigSnapshotService;
import com.redisops.platform.agent.entity.ExecutorInfo;
import com.redisops.platform.agent.service.AgentRegistrationService;
import com.redisops.platform.versionupgrade.service.VersionPackageService;
import com.redisops.platform.operation.model.TaskResultReportRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 验证平台向 Agent 下发服务器切换配置时使用正确的 multipart 接口。 */
class AgentTaskClientTest {

    /** DEPLOY_CONFIG 必须同时携带任务 JSON、Redis 配置和 Sentinel 配置。 */
    @Test
    @SuppressWarnings("unchecked")
    void shouldDispatchConfigFilesAsMultipart() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        AgentRegistrationService registrationService =
                mock(AgentRegistrationService.class);
        VersionPackageService versionPackageService = mock(VersionPackageService.class);
        ExecutorConfigSnapshotService snapshotService =
                mock(ExecutorConfigSnapshotService.class);
        AgentTaskClient client = new AgentTaskClient(
                restTemplate,
                new ObjectMapper(),
                registrationService,
                versionPackageService,
                snapshotService);

        ExecutorInfo executor = new ExecutorInfo();
        executor.setExecutorId("AGENT-N1");
        executor.setHostIp("10.0.0.11");
        executor.setExecutorPort(9101);
        when(registrationService.readDispatchToken(executor)).thenReturn("token-value");
        when(snapshotService.readActiveConfigFiles("AGENT-N1"))
                .thenReturn(new ConfigSnapshotFiles(
                        "redis-config".getBytes(StandardCharsets.UTF_8),
                        repeat("a", 64),
                        "sentinel-config".getBytes(StandardCharsets.UTF_8),
                        repeat("b", 64)));
        when(restTemplate.postForEntity(
                eq("http://10.0.0.11:9101/api/agent/config-task"),
                any(HttpEntity.class),
                eq(String.class)))
                .thenReturn(new ResponseEntity<>("accepted", HttpStatus.ACCEPTED));

        client.dispatch(
                executor,
                "TASK-SWITCH-1",
                5,
                "DEPLOY_CONFIG",
                "{\"redisConfigSha256\":\"" + repeat("a", 64)
                        + "\",\"sentinelConfigSha256\":\"" + repeat("b", 64) + "\"}");

        ArgumentCaptor<HttpEntity> requestCaptor =
                ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(
                eq("http://10.0.0.11:9101/api/agent/config-task"),
                requestCaptor.capture(),
                eq(String.class));
        MultiValueMap<String, Object> body =
                (MultiValueMap<String, Object>) requestCaptor.getValue().getBody();
        assertNotNull(body);
        assertEquals(1, body.get("task").size());
        assertEquals(1, body.get("redisFile").size());
        assertEquals(1, body.get("sentinelFile").size());
    }

    /** 平台应能读取执行器本地保存的最终结果，用于恢复丢失的回调。 */
    @Test
    void shouldQueryPersistedTaskResult() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        AgentRegistrationService registrationService =
                mock(AgentRegistrationService.class);
        AgentTaskClient client = new AgentTaskClient(
                restTemplate,
                new ObjectMapper(),
                registrationService,
                mock(VersionPackageService.class),
                mock(ExecutorConfigSnapshotService.class));

        ExecutorInfo executor = new ExecutorInfo();
        executor.setExecutorId("redis-node-01");
        executor.setHostIp("10.0.0.11");
        executor.setExecutorPort(9101);
        when(registrationService.readDispatchToken(executor)).thenReturn("token-value");

        TaskResultReportRequest expected = new TaskResultReportRequest();
        expected.setTaskNo("TASK202610100001");
        expected.setStepNo(1);
        expected.setAgentId("redis-node-01");
        expected.setOperationType("STOP_SENTINEL");
        expected.setStatus("SUCCESS");
        expected.setResult("{\"success\":true}");
        when(restTemplate.exchange(
                any(URI.class),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(TaskResultReportRequest.class)))
                .thenReturn(ResponseEntity.ok(expected));

        TaskResultReportRequest actual = client.queryTaskResult(
                executor, "TASK202610100001", 1);

        assertNotNull(actual);
        assertEquals("SUCCESS", actual.getStatus());
        assertEquals("redis-node-01", actual.getAgentId());
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
