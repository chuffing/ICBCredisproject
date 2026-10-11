package com.redisops.platform.operation.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.redisops.platform.operation.model.TaskResultReportRequest;
import com.redisops.platform.operation.service.OperationTaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 入口先拒绝非法回调，合法回调的认证与状态冲突仍由业务层处理。 */
class TaskReportControllerTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private OperationTaskService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(OperationTaskService.class);
        mvc = MockMvcBuilders.standaloneSetup(new TaskReportController(service)).build();
    }

    private static ObjectNode validRequest() {
        return JSON.createObjectNode().put("taskNo", "TASK-1").put("stepNo", 1)
                .put("agentId", "AGENT-A").put("operationType", "STOP_REDIS")
                .put("status", "SUCCESS").put("result", "{\"success\":true}");
    }

    static Stream<Arguments> invalidRequests() {
        List<Arguments> requests = new ArrayList<>();
        for (String field : Arrays.asList("taskNo", "agentId", "operationType")) {
            requests.add(Arguments.of(field + " null", validRequest().putNull(field).toString()));
            requests.add(Arguments.of(field + " blank", validRequest().put(field, "  ").toString()));
        }
        requests.add(Arguments.of("stepNo null", validRequest().putNull("stepNo").toString()));
        requests.add(Arguments.of("stepNo zero", validRequest().put("stepNo", 0).toString()));
        requests.add(Arguments.of("stepNo negative", validRequest().put("stepNo", -1).toString()));
        requests.add(Arguments.of("unsupported operation", validRequest().put("operationType", "OTHER").toString()));
        requests.add(Arguments.of("status null", validRequest().putNull("status").toString()));
        for (String value : Arrays.asList("", " ", "RUNNING", "success")) {
            requests.add(Arguments.of("invalid status " + value, validRequest().put("status", value).toString()));
        }
        return requests.stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidRequests")
    void shouldRejectInvalidReportBeforeCallingService(String reason, String body) throws Exception {
        mvc.perform(post("/api/task/report").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_RESULT"));
        verifyNoInteractions(service);
    }

    @Test
    void shouldRejectNullRequestBeforeCallingService() {
        assertEquals(400, new TaskReportController(service).report("Bearer test-token", null).getStatusCodeValue());
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"SUCCESS", "FAILED"})
    void shouldForwardValidReportAndAuthorization(String resultStatus) throws Exception {
        mvc.perform(post("/api/task/report").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer test-token")
                        .content(validRequest().put("status", resultStatus).toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("SUCCESS"));
        ArgumentCaptor<TaskResultReportRequest> request = ArgumentCaptor.forClass(TaskResultReportRequest.class);
        verify(service).receiveResult(eq("Bearer test-token"), request.capture());
        assertEquals("TASK-1", request.getValue().getTaskNo());
        assertEquals(resultStatus, request.getValue().getStatus());
        assertEquals("{\"success\":true}", request.getValue().getResult());
    }

    @Test
    void shouldKeepUnauthorizedResponseFromService() throws Exception {
        doThrow(new SecurityException("Token错误")).when(service).receiveResult(any(), any());
        mvc.perform(post("/api/task/report").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer bad-token").content(validRequest().toString()))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void shouldKeepStepConflictResponseFromService() throws Exception {
        doThrow(new IllegalStateException("步骤状态冲突")).when(service).receiveResult(any(), any());
        mvc.perform(post("/api/task/report").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer test-token").content(validRequest().toString()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STEP_STATUS_CONFLICT"));
    }
}
