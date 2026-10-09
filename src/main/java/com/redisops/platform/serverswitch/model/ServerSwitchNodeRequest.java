package com.redisops.platform.serverswitch.model;

/**
 * 服务器切换请求中的一台新服务器。
 */
public class ServerSwitchNodeRequest {

    private String executorId;

    /** 获取新服务器上的执行器编号。 */
    public String getExecutorId() { return executorId; }

    /** 设置新服务器上的执行器编号。 */
    public void setExecutorId(String executorId) { this.executorId = executorId; }
}
