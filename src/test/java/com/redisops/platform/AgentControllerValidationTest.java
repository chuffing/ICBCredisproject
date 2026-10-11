package com.redisops.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.redisops.platform.agent.config.AgentPlatformProperties;
import com.redisops.platform.agent.configsnapshot.controller.AgentConfigSnapshotController;
import com.redisops.platform.agent.configsnapshot.model.ConfigSnapshotUploadRequest;
import com.redisops.platform.agent.configsnapshot.service.ExecutorConfigSnapshotService;
import com.redisops.platform.agent.configsnapshot.service.impl.ExecutorConfigSnapshotServiceImpl;
import com.redisops.platform.agent.controller.AgentController;
import com.redisops.platform.agent.model.HeartbeatRequest;
import com.redisops.platform.agent.model.RegisterRequest;
import com.redisops.platform.agent.service.AgentRegistrationService;
import com.redisops.platform.agent.service.impl.AgentRegistrationServiceImpl;
import com.redisops.platform.agent.status.controller.ExecutorRuntimeStatusController;
import com.redisops.platform.agent.status.model.MetricReportRequest;
import com.redisops.platform.agent.status.service.ExecutorRuntimeStatusService;
import com.redisops.platform.security.service.ConfigCipherService;
import com.redisops.platform.security.service.TokenCipherService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 验证非法参数在入口拦截，以及认证、配置内容和冲突仍由业务层处理。 */
class AgentControllerValidationTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String TOKEN = "Bearer test-token";
    private AgentRegistrationService registration;
    private ExecutorRuntimeStatusService metrics;
    private ExecutorConfigSnapshotService snapshots;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        registration = mock(AgentRegistrationService.class);
        metrics = mock(ExecutorRuntimeStatusService.class);
        snapshots = mock(ExecutorConfigSnapshotService.class);
        mvc = MockMvcBuilders.standaloneSetup(new AgentController(registration),
                new ExecutorRuntimeStatusController(metrics),
                new AgentConfigSnapshotController(snapshots)).build();
    }

    private static ObjectNode registerRequest() {
        return JSON.createObjectNode().put("registerCode", "test-code")
                .put("agentName", "agent-a").put("hostname", "host-a").put("ip", "127.0.0.1");
    }

    private static ObjectNode heartbeatRequest() {
        return JSON.createObjectNode().put("agentId", "AGENT-A").put("token", "test-token");
    }

    private static ObjectNode metricRequest() {
        ObjectNode request = JSON.createObjectNode().put("agentId", "AGENT-A");
        request.putObject("metric").put("collectTime", 123L);
        return request;
    }

    private static ObjectNode snapshotRequest() {
        return JSON.createObjectNode().put("agentId", "AGENT-A")
                .put("redisConfigBase64", "cmVkaXM=").put("sentinelConfigBase64", "c2VudGluZWw=");
    }

    static Stream<Arguments> invalidRequests() {
        List<Arguments> requests = new ArrayList<>();
        for (String field : Arrays.asList("agentName", "hostname", "ip")) {
            for (String value : Arrays.asList(null, "", "  ")) {
                requests.add(Arguments.of("/api/agent/register", "INVALID_ARGUMENT",
                        registerRequest().put(field, value).toString()));
            }
        }
        for (int port : new int[]{-1, 0, 65536}) {
            requests.add(Arguments.of("/api/agent/register", "INVALID_ARGUMENT",
                    registerRequest().put("agentPort", port).toString()));
        }
        for (String value : Arrays.asList(null, "", "  ")) {
            requests.add(Arguments.of("/api/agent/heartbeat", "INVALID_ARGUMENT",
                    heartbeatRequest().put("agentId", value).toString()));
            requests.add(Arguments.of("/api/metric/report", "INVALID_METRIC",
                    metricRequest().put("agentId", value).toString()));
            for (String field : Arrays.asList("agentId", "redisConfigBase64", "sentinelConfigBase64")) {
                requests.add(Arguments.of("/api/agent/config-snapshot", "INVALID_ARGUMENT",
                        snapshotRequest().put(field, value).toString()));
            }
        }
        requests.add(Arguments.of("/api/metric/report", "INVALID_METRIC",
                metricRequest().putNull("metric").toString()));
        ObjectNode missingCollectTime = metricRequest();
        ((ObjectNode) missingCollectTime.get("metric")).remove("collectTime");
        requests.add(Arguments.of("/api/metric/report", "INVALID_METRIC", missingCollectTime.toString()));
        return requests.stream();
    }

    @ParameterizedTest
    @MethodSource("invalidRequests")
    void shouldRejectInvalidBodyBeforeCallingService(String path, String code, String body) throws Exception {
        mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(code));
        verifyNoInteractions(registration, metrics, snapshots);
    }

    @Test
    void shouldRejectNullBodiesAtControllerEntry() {
        AgentController agent = new AgentController(registration);
        assertEquals(400, agent.register(null).getStatusCodeValue());
        assertEquals(400, agent.heartbeat(TOKEN, null).getStatusCodeValue());
        assertEquals(400, new ExecutorRuntimeStatusController(metrics).report(TOKEN, null).getStatusCodeValue());
        assertEquals(400, new AgentConfigSnapshotController(snapshots)
                .initializeOrVerify(TOKEN, null).getStatusCodeValue());
        verifyNoInteractions(registration, metrics, snapshots);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    void shouldRejectBlankSnapshotQueryBeforeCallingService(String agentId) throws Exception {
        if (agentId == null) {
            mvc.perform(get("/api/agent/config-snapshot")).andExpect(status().isBadRequest());
            assertEquals(400, new AgentConfigSnapshotController(snapshots)
                    .status(TOKEN, null).getStatusCodeValue());
        } else {
            mvc.perform(get("/api/agent/config-snapshot").param("agentId", agentId))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));
        }
        verifyNoInteractions(registration, metrics, snapshots);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"1", "65535"})
    void shouldAcceptOptionalPortAndValidPortBoundaries(String port) throws Exception {
        ObjectNode body = registerRequest();
        if (port == null) body.putNull("agentPort");
        else if (!port.isEmpty()) body.put("agentPort", Integer.parseInt(port));
        mvc.perform(post("/api/agent/register").contentType(MediaType.APPLICATION_JSON).content(body.toString()))
                .andExpect(status().isOk());
        ArgumentCaptor<RegisterRequest> request = ArgumentCaptor.forClass(RegisterRequest.class);
        verify(registration).register(request.capture());
        assertEquals("agent-a", request.getValue().getAgentName());
        assertEquals(port == null || port.isEmpty() ? null : Integer.valueOf(port), request.getValue().getAgentPort());
    }

    @Test
    void shouldForwardValidRequestsAndAuthorizationWithoutRequiringOptionalFields() throws Exception {
        mvc.perform(post("/api/agent/heartbeat").header("Authorization", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content(heartbeatRequest().toString()))
                .andExpect(status().isOk());
        mvc.perform(post("/api/metric/report").header("Authorization", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content(metricRequest().toString()))
                .andExpect(status().isOk());
        mvc.perform(post("/api/agent/config-snapshot").header("Authorization", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content(snapshotRequest().toString()))
                .andExpect(status().isOk());
        mvc.perform(get("/api/agent/config-snapshot").header("Authorization", TOKEN).param("agentId", "AGENT-A"))
                .andExpect(status().isOk());
        ArgumentCaptor<HeartbeatRequest> heartbeat = ArgumentCaptor.forClass(HeartbeatRequest.class);
        verify(registration).heartbeat(eq(TOKEN), heartbeat.capture());
        assertEquals("AGENT-A", heartbeat.getValue().getAgentId());
        ArgumentCaptor<MetricReportRequest> metric = ArgumentCaptor.forClass(MetricReportRequest.class);
        verify(metrics).receive(eq(TOKEN), metric.capture());
        assertEquals(Long.valueOf(123), metric.getValue().getMetric().getCollectTime());
        ArgumentCaptor<ConfigSnapshotUploadRequest> upload = ArgumentCaptor.forClass(ConfigSnapshotUploadRequest.class);
        verify(snapshots).initializeOrVerify(eq(TOKEN), upload.capture());
        assertEquals("cmVkaXM=", upload.getValue().getRedisConfigBase64());
        verify(snapshots).getStatus(TOKEN, "AGENT-A");
    }

    @Test
    void shouldPreserveUnauthorizedResponsesFromServices() throws Exception {
        doThrow(new SecurityException("注册码错误")).when(registration).register(any());
        doThrow(new SecurityException("Token无效")).when(registration).heartbeat(any(), any());
        doThrow(new SecurityException("Token无效")).when(metrics).receive(any(), any());
        doThrow(new SecurityException("Token无效")).when(snapshots).initializeOrVerify(any(), any());
        doThrow(new SecurityException("Token无效")).when(snapshots).getStatus(any(), any());
        mvc.perform(post("/api/agent/register").contentType(MediaType.APPLICATION_JSON).content(registerRequest().toString()))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("REGISTER_CODE_ERROR"));
        for (String path : Arrays.asList("/api/agent/heartbeat", "/api/metric/report", "/api/agent/config-snapshot")) {
            ObjectNode body = path.contains("heartbeat") ? heartbeatRequest()
                    : path.contains("metric") ? metricRequest() : snapshotRequest();
            mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body.toString()))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        }
        mvc.perform(get("/api/agent/config-snapshot").param("agentId", "AGENT-A"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void shouldPreserveSnapshotConflictResponse() throws Exception {
        doThrow(new IllegalStateException("配置不一致")).when(snapshots).initializeOrVerify(any(), any());
        mvc.perform(post("/api/agent/config-snapshot").contentType(MediaType.APPLICATION_JSON)
                        .content(snapshotRequest().toString()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONFIG_SNAPSHOT_CONFLICT"));
    }

    @Test
    void shouldStillAuthenticateRegistrationInRealService() throws Exception {
        AgentPlatformProperties properties = new AgentPlatformProperties();
        properties.setRegisterCode("expected-code");
        TokenCipherService cipher = mock(TokenCipherService.class);
        AgentRegistrationServiceImpl service = new AgentRegistrationServiceImpl(properties, cipher);
        MockMvc realMvc = MockMvcBuilders.standaloneSetup(new AgentController(service)).build();
        realMvc.perform(post("/api/agent/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registerRequest().toString()))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("REGISTER_CODE_ERROR"));
        verifyNoInteractions(cipher);
    }

    @Test
    void shouldStillValidateConfigContentInRealService() throws Exception {
        ConfigCipherService cipher = mock(ConfigCipherService.class);
        ExecutorConfigSnapshotServiceImpl service = new ExecutorConfigSnapshotServiceImpl(registration, cipher);
        MockMvc realMvc = MockMvcBuilders.standaloneSetup(new AgentConfigSnapshotController(service)).build();
        realMvc.perform(post("/api/agent/config-snapshot").header("Authorization", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(snapshotRequest().put("redisConfigBase64", "%%%invalid%%%").toString()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"))
                .andExpect(jsonPath("$.message").value("redis.conf 不是合法Base64内容"));
        verify(registration).authenticateExecutor("AGENT-A", TOKEN);
        verifyNoInteractions(cipher);
    }
}
