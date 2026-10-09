package com.redisops.platform.versionupgrade.model;

/**
 * 版本升级请求中的一台目标服务器。
 */
public class VersionUpgradeNodeRequest {

    private String executorId;

    /** 获取执行器编号。 */
    public String getExecutorId() { return executorId; }

    /** 设置执行器编号。 */
    public void setExecutorId(String executorId) { this.executorId = executorId; }
}
