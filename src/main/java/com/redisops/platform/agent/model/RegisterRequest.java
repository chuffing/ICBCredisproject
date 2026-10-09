package com.redisops.platform.agent.model;

import java.util.Map;

/**
 * 执行器首次注册请求。
 */
public class RegisterRequest {

    private String registerCode;
    private String agentName;
    private String hostname;
    private String ip;
    private Integer agentPort;
    private String os;
    private Map<String, String> redisInfo;

    /** 获取注册码。 */
    public String getRegisterCode() { return registerCode; }

    /** 设置注册码。 */
    public void setRegisterCode(String registerCode) { this.registerCode = registerCode; }

    /** 获取执行器名称。 */
    public String getAgentName() { return agentName; }

    /** 设置执行器名称。 */
    public void setAgentName(String agentName) { this.agentName = agentName; }

    /** 获取主机名。 */
    public String getHostname() { return hostname; }

    /** 设置主机名。 */
    public void setHostname(String hostname) { this.hostname = hostname; }

    /** 获取执行器 IP。 */
    public String getIp() { return ip; }

    /** 设置执行器 IP。 */
    public void setIp(String ip) { this.ip = ip; }

    /** 获取执行器端口。 */
    public Integer getAgentPort() { return agentPort; }

    /** 设置执行器端口。 */
    public void setAgentPort(Integer agentPort) { this.agentPort = agentPort; }

    /** 获取操作系统名称。 */
    public String getOs() { return os; }

    /** 设置操作系统名称。 */
    public void setOs(String os) { this.os = os; }

    /** 获取 Redis 基础信息。 */
    public Map<String, String> getRedisInfo() { return redisInfo; }

    /** 设置 Redis 基础信息。 */
    public void setRedisInfo(Map<String, String> redisInfo) { this.redisInfo = redisInfo; }
}
