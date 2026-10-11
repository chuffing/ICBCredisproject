package com.redisops.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redisops.platform.agent.config.AgentPlatformProperties;
import com.redisops.platform.agent.configsnapshot.entity.ExecutorConfigSnapshot;
import com.redisops.platform.agent.configsnapshot.model.ConfigSnapshotFiles;
import com.redisops.platform.agent.configsnapshot.model.ConfigSnapshotStatusData;
import com.redisops.platform.agent.entity.ExecutorInfo;
import com.redisops.platform.agent.model.RegisterData;
import com.redisops.platform.agent.status.model.MetricReportRequest;
import com.redisops.platform.common.model.ApiResponse;
import com.redisops.platform.redisinfo.entity.RedisInfo;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 验证生成访问器后，HTTP JSON 协议、敏感字段隐藏和配置副本仍保持原语义。 */
class LombokModelContractTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void shouldKeepNestedMetricAndBooleanResponseJsonProperties() throws Exception {
        MetricReportRequest request = mapper.readValue(
                "{\"agentId\":\"AGENT-A\",\"metric\":{\"status\":\"up\",\"masterPort\":\"6379\"}}",
                MetricReportRequest.class);
        assertEquals("AGENT-A", request.getAgentId());
        assertEquals("6379", request.getMetric().getMasterPort());
        assertEquals("up", request.getMetric().getStatus());

        JsonNode response = mapper.valueToTree(ApiResponse.success("ok",
                new ConfigSnapshotStatusData(true, "redis-sha", "sentinel-sha")));
        assertEquals("SUCCESS", response.get("code").asText());
        assertTrue(response.get("data").get("initialized").asBoolean());
        assertFalse(response.get("data").has("isInitialized"));
        assertEquals("redis-sha", response.get("data").get("redisConfigSha256").asText());
    }

    @Test
    void shouldHideInternalCiphertextInJsonAndGeneratedToString() {
        ExecutorInfo executor = new ExecutorInfo();
        executor.setExecutorId("AGENT-A");
        executor.setTokenCiphertext("private-token");
        ExecutorConfigSnapshot snapshot = new ExecutorConfigSnapshot();
        snapshot.setRedisConfigCiphertext("private-redis-config");
        snapshot.setSentinelConfigCiphertext("private-sentinel-config");
        RedisInfo redis = new RedisInfo();
        redis.setRedisPasswordCiphertext("private-password");

        assertFalse(mapper.valueToTree(executor).has("tokenCiphertext"));
        JsonNode snapshotJson = mapper.valueToTree(snapshot);
        assertFalse(snapshotJson.has("redisConfigCiphertext"));
        assertFalse(snapshotJson.has("sentinelConfigCiphertext"));
        assertFalse(mapper.valueToTree(redis).has("redisPasswordCiphertext"));
        assertFalse(executor.toString().contains("private-token"));
        assertFalse(snapshot.toString().contains("private-redis-config"));
        assertFalse(snapshot.toString().contains("private-sentinel-config"));
        assertFalse(redis.toString().contains("private-password"));
    }

    @Test
    void shouldKeepRegistrationTokenInResponseButOutOfGeneratedToString() {
        RegisterData registration = new RegisterData("AGENT-A", "private-token");
        assertEquals("private-token", mapper.valueToTree(registration).get("token").asText());
        assertFalse(registration.toString().contains("private-token"));

        AgentPlatformProperties properties = new AgentPlatformProperties();
        properties.setRegisterCode("private-register-code");
        properties.setTokenSecret("private-token-key");
        properties.setConfigSecret("private-config-key");
        assertFalse(properties.toString().contains("private-"));
    }

    @Test
    void shouldStillCopyConfigurationBytesOnConstructionAndRead() {
        byte[] redis = {1, 2};
        byte[] sentinel = {3, 4};
        ConfigSnapshotFiles files = new ConfigSnapshotFiles(redis, "redis-sha", sentinel, "sentinel-sha");
        redis[0] = 9;
        sentinel[0] = 9;
        files.getRedisConfig()[0] = 8;
        files.getSentinelConfig()[0] = 8;
        assertArrayEquals(new byte[]{1, 2}, files.getRedisConfig());
        assertArrayEquals(new byte[]{3, 4}, files.getSentinelConfig());
    }
}
