package com.danuvius.platform.contract;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.danuvius.platform.common.ResultUtils;
import com.danuvius.platform.common.exception.ParameterException;
import com.danuvius.platform.model.dto.agent.AgentHeartbeatRequest;
import com.danuvius.platform.model.dto.agent.AgentRegisterRequest;
import com.danuvius.platform.model.dto.agent.AgentRegisterResultDTO;
import com.danuvius.platform.util.validate.AgentHeartbeatRequestValidator;
import com.danuvius.platform.util.validate.AgentRegisterRequestValidator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 以执行器当前实际JSON字段验证注册和心跳接口兼容性。 */
class AgentRegistrationContractTest {

    @Test
    void currentAgentRegistrationJsonCannotPassPlatformValidation() {
        String agentJson = "{"
                + "\"registerCode\":\"test-code\","
                + "\"agentName\":\"agent-1\","
                + "\"hostname\":\"host-1\","
                + "\"ip\":\"10.0.0.1\","
                + "\"agentPort\":9101,"
                + "\"os\":\"Linux\","
                + "\"redisInfo\":{\"host\":\"10.0.0.1\",\"port\":\"6379\"}"
                + "}";
        AgentRegisterRequest request = JSON.parseObject(
                agentJson, AgentRegisterRequest.class);

        assertThrows(ParameterException.class,
                () -> AgentRegisterRequestValidator.validate(request));
    }

    @Test
    void currentAgentHeartbeatJsonCannotPassPlatformValidation() {
        String agentJson = "{"
                + "\"token\":\"test-token\","
                + "\"agentId\":\"agent-1\","
                + "\"status\":\"UP\","
                + "\"hostname\":\"host-1\","
                + "\"ip\":\"10.0.0.1\","
                + "\"lastCollectTime\":1792947600000"
                + "}";
        AgentHeartbeatRequest request = JSON.parseObject(
                agentJson, AgentHeartbeatRequest.class);

        assertThrows(ParameterException.class,
                () -> AgentHeartbeatRequestValidator.validate(
                        "Bearer test-token", request));
    }

    @Test
    void currentPlatformRegistrationResponseDoesNotContainAgentExpectedKeys() {
        String responseJson = JSON.toJSONString(ResultUtils.success(
                new AgentRegisterResultDTO("test-token", "instance-1")));
        JSONObject response = JSON.parseObject(responseJson);

        // BaseResponse当前没有getter，序列化后连data字段都不存在。
        assertFalse(response.containsKey("data"));
    }
}
