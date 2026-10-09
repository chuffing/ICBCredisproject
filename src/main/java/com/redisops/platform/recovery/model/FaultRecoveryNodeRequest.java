package com.redisops.platform.recovery.model;

/**
 * 故障恢复中一台服务器的执行器信息。
 */
public class FaultRecoveryNodeRequest {

    private String executorId;

    /** 获取负责本机操作的执行器编号。 */
    public String getExecutorId() {
        return executorId;
    }

    /** 设置负责本机操作的执行器编号。 */
    public void setExecutorId(String executorId) {
        this.executorId = executorId;
    }

}
