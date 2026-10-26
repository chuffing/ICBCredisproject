package com.danuvius.platform.util.http;

import com.danuvius.platform.common.exception.AgentTaskDispatchUnknownException;
import com.danuvius.platform.common.exception.AgentTaskRejectedException;
import com.danuvius.platform.model.dto.AgentTaskRequestDTO;
import com.danuvius.platform.model.entity.ExecutorInfo;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 使用本机隔离HTTP服务验证平台与执行器的任务下发协议。 */
class AgentTaskClientHttpContractTest {

    private static final String TASK_NO = "TASK202610260001";

    private HttpServer server;
    private ExecutorInfo executor;
    private AgentTaskRequestDTO request;
    private final AtomicReference<String> responseBody = new AtomicReference<>();
    private final AtomicReference<Integer> responseStatus = new AtomicReference<>();
    private final AtomicReference<String> receivedPath = new AtomicReference<>();
    private final AtomicReference<String> receivedAuthorization = new AtomicReference<>();
    private final AtomicReference<String> receivedContentType = new AtomicReference<>();
    private final AtomicReference<String> receivedBody = new AtomicReference<>();

    @TempDir
    Path temporaryDirectory;

    @BeforeEach
    void setUp() throws IOException {
        responseStatus.set(200);
        responseBody.set(acceptedBody(TASK_NO));

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();

        executor = new ExecutorInfo();
        executor.setId("executor-1");
        executor.setHostIp("127.0.0.1");
        executor.setExecutorPort(String.valueOf(server.getAddress().getPort()));
        executor.setExecutorToken("test-token");

        request = new AgentTaskRequestDTO();
        request.setTaskNo(TASK_NO);
        request.setStepNo(1);
        request.setOperationType("START_REDIS");
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void shouldDispatchJsonTaskWithExpectedPathTokenAndBody() {
        new AgentTaskClient().dispatchTask(executor, request);

        assertEquals("/api/agent/task", receivedPath.get());
        assertEquals("Bearer test-token", receivedAuthorization.get());
        assertTrue(receivedContentType.get().startsWith("application/json"));
        assertTrue(receivedBody.get().contains(TASK_NO));
        assertTrue(receivedBody.get().contains("START_REDIS"));
    }

    @Test
    void shouldTreatKnownBusinessRejectionAsRejected() {
        responseBody.set("{\"code\":503,\"msg\":\"执行器繁忙，任务未接收\","
                + "\"data\":{\"taskNo\":\"" + TASK_NO + "\"}}");

        AgentTaskRejectedException exception = assertThrows(
                AgentTaskRejectedException.class,
                () -> new AgentTaskClient().dispatchTask(executor, request));

        assertEquals("执行器繁忙，任务未接收", exception.getMessage());
    }

    @Test
    void shouldTreatUnauthorizedHttpStatusAsRejected() {
        responseStatus.set(401);
        responseBody.set("{}");

        assertThrows(AgentTaskRejectedException.class,
                () -> new AgentTaskClient().dispatchTask(executor, request));
    }

    @Test
    void shouldTreatMalformedOrMismatchedResponseAsUnknown() {
        responseBody.set("not-json");
        assertThrows(AgentTaskDispatchUnknownException.class,
                () -> new AgentTaskClient().dispatchTask(executor, request));

        responseBody.set(acceptedBody("TASK202610260999"));
        assertThrows(AgentTaskDispatchUnknownException.class,
                () -> new AgentTaskClient().dispatchTask(executor, request));
    }

    @Test
    void shouldUploadVersionPackageUsingExpectedMultipartNames() throws IOException {
        File packageFile = temporaryDirectory.resolve("redis-7.2.5.tar.gz").toFile();
        Files.write(packageFile.toPath(), "package-content".getBytes(StandardCharsets.UTF_8));

        new AgentTaskClient().dispatchVersionTask(executor, request, packageFile);

        assertEquals("/api/agent/version/deploy", receivedPath.get());
        assertTrue(receivedContentType.get().startsWith("multipart/form-data"));
        assertTrue(receivedBody.get().contains("name=\"task\""));
        assertTrue(receivedBody.get().contains("name=\"file\""));
        assertTrue(receivedBody.get().contains("redis-7.2.5.tar.gz"));
    }

    @Test
    void shouldUploadBothConfigsUsingExpectedMultipartNames() throws IOException {
        File redisFile = temporaryDirectory.resolve("redis.conf").toFile();
        File sentinelFile = temporaryDirectory.resolve("sentinel.conf").toFile();
        Files.write(redisFile.toPath(), "redis-config".getBytes(StandardCharsets.UTF_8));
        Files.write(sentinelFile.toPath(), "sentinel-config".getBytes(StandardCharsets.UTF_8));

        new AgentTaskClient().dispatchConfigTask(
                executor, request, redisFile, sentinelFile);

        assertEquals("/api/agent/config/deploy", receivedPath.get());
        assertTrue(receivedBody.get().contains("name=\"task\""));
        assertTrue(receivedBody.get().contains("name=\"redisFile\""));
        assertTrue(receivedBody.get().contains("name=\"sentinelFile\""));
    }

    private void handle(HttpExchange exchange) throws IOException {
        receivedPath.set(exchange.getRequestURI().getPath());
        receivedAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
        receivedContentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
        receivedBody.set(readFully(exchange.getRequestBody()));

        byte[] bytes = responseBody.get().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json;charset=UTF-8");
        exchange.sendResponseHeaders(responseStatus.get(), bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private String readFully(InputStream inputStream) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int length;
        while ((length = inputStream.read(buffer)) >= 0) {
            output.write(buffer, 0, length);
        }
        return new String(output.toByteArray(), StandardCharsets.ISO_8859_1);
    }

    private String acceptedBody(String taskNo) {
        return "{\"code\":202,\"msg\":\"任务已接收\",\"data\":{\"taskNo\":\""
                + taskNo + "\"}}";
    }
}
