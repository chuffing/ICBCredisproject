package com.redisops.platform.agent.model;

import java.util.Map;

/**
 * 执行器每 30 秒发送的心跳请求。
 */
public class HeartbeatRequest {

    private String token;
    private String agentId;
    private String status;
    private String hostname;
    private String ip;
    private String cpuUsage;
    private String memUsage;
    private Long lastCollectTime;
    private Map<String, String> extra;

    /** 获取请求体中的令牌。 */
    public String getToken() { return token; }

    /** 设置请求体中的令牌。 */
    public void setToken(String token) { this.token = token; }

    /** 获取执行器编号。 */
    public String getAgentId() { return agentId; }

    /** 设置执行器编号。 */
    public void setAgentId(String agentId) { this.agentId = agentId; }

    /** 获取运行状态。 */
    public String getStatus() { return status; }

    /** 设置运行状态。 */
    public void setStatus(String status) { this.status = status; }

    /** 获取主机名。 */
    public String getHostname() { return hostname; }

    /** 设置主机名。 */
    public void setHostname(String hostname) { this.hostname = hostname; }

    /** 获取 IP。 */
    public String getIp() { return ip; }

    /** 设置 IP。 */
    public void setIp(String ip) { this.ip = ip; }

    /** 获取 CPU 使用率。 */
    public String getCpuUsage() { return cpuUsage; }

    /** 设置 CPU 使用率。 */
    public void setCpuUsage(String cpuUsage) { this.cpuUsage = cpuUsage; }

    /** 获取内存使用率。 */
    public String getMemUsage() { return memUsage; }

    /** 设置内存使用率。 */
    public void setMemUsage(String memUsage) { this.memUsage = memUsage; }

    /** 获取最近采集时间。 */
    public Long getLastCollectTime() { return lastCollectTime; }

    /** 设置最近采集时间。 */
    public void setLastCollectTime(Long lastCollectTime) { this.lastCollectTime = lastCollectTime; }

    /** 获取扩展信息。 */
    public Map<String, String> getExtra() { return extra; }

    /** 设置扩展信息。 */
    public void setExtra(Map<String, String> extra) { this.extra = extra; }
}
