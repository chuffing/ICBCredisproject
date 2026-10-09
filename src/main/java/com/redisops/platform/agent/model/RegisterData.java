package com.redisops.platform.agent.model;

/**
 * 注册成功后返回给执行器的身份凭证。
 */
public class RegisterData {

    private final String agentId;
    private final String token;

    /**
     * 创建注册结果数据。
     *
     * @param agentId 平台分配的执行器编号
     * @param token 后续通信使用的认证令牌
     */
    public RegisterData(String agentId, String token) {
        this.agentId = agentId;
        this.token = token;
    }

    /** 获取执行器编号。 */
    public String getAgentId() { return agentId; }

    /** 获取认证令牌。 */
    public String getToken() { return token; }
}
