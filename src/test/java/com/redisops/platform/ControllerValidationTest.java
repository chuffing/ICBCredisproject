package com.redisops.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.redisops.platform.operation.controller.OperationTaskController;
import com.redisops.platform.operation.service.OperationTaskService;
import com.redisops.platform.recovery.controller.FaultRecoveryController;
import com.redisops.platform.recovery.model.FaultRecoveryTaskRequest;
import com.redisops.platform.recovery.service.FaultRecoveryService;
import com.redisops.platform.redisinfo.controller.RedisInfoController;
import com.redisops.platform.redisinfo.service.RedisInfoService;
import com.redisops.platform.serverswitch.controller.ServerSwitchController;
import com.redisops.platform.serverswitch.model.ServerSwitchTaskRequest;
import com.redisops.platform.serverswitch.service.ServerSwitchService;
import com.redisops.platform.versionupgrade.config.VersionPackageProperties;
import com.redisops.platform.versionupgrade.controller.VersionPackageController;
import com.redisops.platform.versionupgrade.controller.VersionUpgradeController;
import com.redisops.platform.versionupgrade.model.VersionUpgradeTaskRequest;
import com.redisops.platform.versionupgrade.service.VersionPackageService;
import com.redisops.platform.versionupgrade.service.VersionUpgradeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 非法 HTTP 参数必须在业务调用前被拒绝，合法请求和业务冲突仍保持原响应。 */
class ControllerValidationTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String RECOVERY = "/api/fault-recovery-tasks";
    private static final String UPGRADE = "/api/version-upgrade-tasks";
    private static final String SWITCH = "/api/server-switch-tasks";
    private static final String INITIALIZE = "/api/redis-info/initialize";
    private static final String OPERATION = "/api/operation-tasks";

    private FaultRecoveryService recovery;
    private VersionUpgradeService upgrade;
    private ServerSwitchService serverSwitch;
    private RedisInfoService redisInfo;
    private OperationTaskService operations;
    private VersionPackageService packages;
    private VersionPackageController packageController;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        recovery = mock(FaultRecoveryService.class);
        upgrade = mock(VersionUpgradeService.class);
        serverSwitch = mock(ServerSwitchService.class);
        redisInfo = mock(RedisInfoService.class);
        operations = mock(OperationTaskService.class);
        packages = mock(VersionPackageService.class);
        VersionPackageProperties properties = new VersionPackageProperties();
        properties.setMaxBytes(16);
        packageController = new VersionPackageController(packages, properties);
        mvc = MockMvcBuilders.standaloneSetup(new FaultRecoveryController(recovery),
                new VersionUpgradeController(upgrade), new ServerSwitchController(serverSwitch),
                new RedisInfoController(redisInfo), new OperationTaskController(operations),
                packageController).build();
    }

    @ParameterizedTest(name = "{0}: {1}")
    @MethodSource("invalidJsonRequests")
    void shouldRejectInvalidJsonWithoutCallingServices(String path, String reason, String body) throws Exception {
        mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(recovery, upgrade, serverSwitch, redisInfo, operations, packages);
    }

    static Stream<Arguments> invalidJsonRequests() throws Exception {
        List<Arguments> cases = new ArrayList<>();
        for (String path : Arrays.asList(RECOVERY, UPGRADE, SWITCH, INITIALIZE, OPERATION)) {
            cases.add(Arguments.of(path, "null request", "null"));
            ObjectNode valid = validRequest(path);
            String[] required = INITIALIZE.equals(path)
                    ? new String[]{"redisPassword", "redisVersion"}
                    : new String[]{"createdBy", "operationReason"};
            for (String field : required) {
                invalid(cases, path, field + " blank", valid.deepCopy().put(field, "  "));
                invalid(cases, path, field + " null", valid.deepCopy().putNull(field));
            }
            if (OPERATION.equals(path)) {
                invalid(cases, path, "unsupported task type", valid.deepCopy().put("taskType", "OTHER"));
                invalid(cases, path, "missing task type", valid.deepCopy().putNull("taskType"));
                invalid(cases, path, "missing steps", valid.deepCopy().putNull("steps"));
                ObjectNode value = valid.deepCopy();
                value.putArray("steps");
                invalid(cases, path, "empty steps", value);
                value = valid.deepCopy();
                value.putArray("steps").addNull();
                invalid(cases, path, "null step", value);
                for (String field : Arrays.asList("stepNo", "executorId", "operationType")) {
                    value = valid.deepCopy();
                    ((ObjectNode) value.withArray("steps").get(0)).putNull(field);
                    invalid(cases, path, "missing step " + field, value);
                }
                value = valid.deepCopy();
                ((ObjectNode) value.withArray("steps").get(0)).put("stepNo", 0);
                invalid(cases, path, "nonpositive stepNo", value);
                value = valid.deepCopy();
                ((ObjectNode) value.withArray("steps").get(0)).put("operationType", "OTHER");
                invalid(cases, path, "unsupported operation", value);
                continue;
            }
            if (!INITIALIZE.equals(path)) {
                String master = SWITCH.equals(path) ? "masterHostIp" : "masterExecutorId";
                invalid(cases, path, "missing master", valid.deepCopy().put(master, "  "));
            }
            if (UPGRADE.equals(path) || SWITCH.equals(path)) {
                invalid(cases, path, "missing package", valid.deepCopy().putNull("versionPackageId"));
                invalid(cases, path, "nonpositive package", valid.deepCopy().put("versionPackageId", 0));
            }
            invalid(cases, path, "nodes null", valid.deepCopy().putNull("nodes"));
            for (int count : new int[]{0, 2, 4}) {
                ObjectNode value = valid.deepCopy();
                ArrayNode nodes = value.putArray("nodes");
                for (int index = 0; index < count; index++) {
                    nodes.add(valid.withArray("nodes").get(index % 3).deepCopy());
                }
                invalid(cases, path, "node count " + count, value);
            }
            ObjectNode value = valid.deepCopy();
            value.withArray("nodes").set(0, JSON.nullNode());
            invalid(cases, path, "null node", value);
            String identity = (INITIALIZE.equals(path) || SWITCH.equals(path)) ? "hostIp" : "executorId";
            value = valid.deepCopy();
            ((ObjectNode) value.withArray("nodes").get(0)).put(identity, "  ");
            invalid(cases, path, "blank node identity", value);
            value = valid.deepCopy();
            ((ObjectNode) value.withArray("nodes").get(1))
                    .put(identity, " " + value.withArray("nodes").get(0).get(identity).asText() + " ");
            invalid(cases, path, "duplicate node identity after trimming", value);
            for (int port : new int[]{0, 65536}) {
                invalid(cases, path, "invalid Redis port " + port, valid.deepCopy().put("redisPort", port));
                if (INITIALIZE.equals(path)) {
                    invalid(cases, path, "invalid Sentinel port " + port, valid.deepCopy().put("sentinelPort", port));
                }
            }
            if (INITIALIZE.equals(path)) {
                value = valid.deepCopy();
                ((ObjectNode) value.withArray("nodes").get(0)).put("role", "OTHER");
                invalid(cases, path, "unsupported role", value);
                value = valid.deepCopy();
                ((ObjectNode) value.withArray("nodes").get(0)).putNull("role");
                invalid(cases, path, "missing role", value);
            }
        }
        return cases.stream();
    }

    private static void invalid(List<Arguments> cases, String path, String reason, ObjectNode request) {
        cases.add(Arguments.of(path, reason, request.toString()));
    }

    private static ObjectNode validRequest(String path) throws Exception {
        if (INITIALIZE.equals(path)) {
            return (ObjectNode) JSON.readTree("{\"redisPassword\":\"test\",\"redisVersion\":\"7.2.5\","
                    + "\"nodes\":[{\"hostIp\":\"10.0.0.1\",\"role\":\"MASTER\"},"
                    + "{\"hostIp\":\"10.0.0.2\",\"role\":\"REPLICA\"},{\"hostIp\":\"10.0.0.3\",\"role\":\"REPLICA\"}]}");
        }
        if (OPERATION.equals(path)) {
            return (ObjectNode) JSON.readTree("{\"createdBy\":\"admin\",\"operationReason\":\"test\","
                    + "\"taskType\":\"FAULT_RECOVERY\",\"steps\":[{\"stepNo\":1,\"executorId\":\"A\",\"operationType\":\"STOP_REDIS\"}]}");
        }
        ObjectNode result = (ObjectNode) JSON.readTree("{\"createdBy\":\"admin\",\"operationReason\":\"test\","
                + "\"masterExecutorId\":\"A\",\"versionPackageId\":1,\"nodes\":[{\"executorId\":\"A\"},"
                + "{\"executorId\":\"B\"},{\"executorId\":\"C\"}]}");
        if (RECOVERY.equals(path)) {
            result.remove("versionPackageId");
        }
        if (SWITCH.equals(path)) {
            result.remove("masterExecutorId");
            result.put("masterHostIp", "10.0.0.1");
            ArrayNode nodes = result.putArray("nodes");
            for (int index = 1; index <= 3; index++) {
                nodes.addObject().put("hostIp", "10.0.0." + index);
            }
        }
        return result;
    }

    static Stream<Arguments> validJsonRequests() throws Exception {
        List<Arguments> cases = new ArrayList<>();
        for (String path : Arrays.asList(RECOVERY, UPGRADE, SWITCH, INITIALIZE, OPERATION)) {
            cases.add(Arguments.of(path, validRequest(path).toString(), INITIALIZE.equals(path) ? 200 : 201));
        }
        return cases.stream();
    }

    @ParameterizedTest
    @MethodSource("validJsonRequests")
    void shouldForwardValidRequestsIncludingDefaultPorts(String path, String body, int expectedStatus) throws Exception {
        mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(expectedStatus)).andExpect(jsonPath("$.code").value("SUCCESS"));
        if (RECOVERY.equals(path)) verify(recovery).createAndDispatch(any());
        if (UPGRADE.equals(path)) verify(upgrade).createAndDispatch(any());
        if (SWITCH.equals(path)) verify(serverSwitch).createAndDispatch(any());
        if (INITIALIZE.equals(path)) verify(redisInfo).initialize(any());
        if (OPERATION.equals(path)) verify(operations).createAndDispatch(any());
    }

    @Test
    void shouldStillReturnBusinessConflictAfterBasicValidation() throws Exception {
        when(recovery.createAndDispatch(any(FaultRecoveryTaskRequest.class)))
                .thenThrow(new IllegalStateException("执行器离线"));
        when(upgrade.createAndDispatch(any(VersionUpgradeTaskRequest.class)))
                .thenThrow(new IllegalStateException("版本包不可用"));
        when(serverSwitch.createAndDispatch(any(ServerSwitchTaskRequest.class)))
                .thenThrow(new IllegalStateException("配置快照不可用"));
        for (String path : Arrays.asList(RECOVERY, UPGRADE, SWITCH)) {
            mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(validRequest(path).toString()))
                    .andExpect(status().isConflict());
        }
    }

    static Stream<Arguments> invalidUploads() {
        return Stream.of(
                Arguments.of(" ", "admin", "redis.tar.gz", 1),
                Arguments.of("../7.2.5", "admin", "redis.tar.gz", 1),
                Arguments.of("7.2.5", " ", "redis.tar.gz", 1),
                Arguments.of("7.2.5", "admin", "", 1),
                Arguments.of("7.2.5", "admin", "redis.zip", 1),
                Arguments.of("7.2.5", "admin", "redis.tar.gz", 0),
                Arguments.of("7.2.5", "admin", "redis.tar.gz", 17));
    }

    @ParameterizedTest
    @MethodSource("invalidUploads")
    void shouldRejectUploadParametersBeforeReadingArchive(String version, String uploadedBy,
                                                          String filename, int bytes) throws Exception {
        mvc.perform(multipart("/api/version-packages")
                        .file(new MockMultipartFile("file", filename, "application/gzip", new byte[bytes]))
                        .param("version", version).param("uploadedBy", uploadedBy))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_VERSION_PACKAGE"));
        verifyNoInteractions(packages);
    }

    @Test
    void shouldRejectBlankTaskNumberAndKeepUnknownTaskAs404() throws Exception {
        assertEquals(400, new OperationTaskController(operations).detail(" ").getStatusCodeValue());
        verifyNoInteractions(operations);
        when(operations.getTaskDetail("unknown")).thenThrow(new IllegalArgumentException("任务不存在"));
        mvc.perform(get(OPERATION + "/unknown")).andExpect(status().isNotFound());
    }

    @Test
    void shouldForwardValidMultipartUpload() {
        com.redisops.platform.versionupgrade.entity.RedisVersionPackage entity =
                new com.redisops.platform.versionupgrade.entity.RedisVersionPackage();
        when(packages.upload(any(), any(), any())).thenReturn(
                new com.redisops.platform.versionupgrade.model.VersionPackageData(entity));
        MockMultipartFile file = new MockMultipartFile("file", "redis.tar.gz", "application/gzip", new byte[]{1});
        assertEquals(201, packageController.upload("7.2.5", "admin", file).getStatusCodeValue());
        verify(packages).upload("7.2.5", "admin", file);
    }
}
