package com.redisops.platform.agent.model;

import lombok.Data;
import lombok.ToString;

/**
 * 注册成功后返回给执行器的身份凭证。
 */
@Data
public class RegisterData {

    private final String agentId;
    @ToString.Exclude
    private final String token;
}
