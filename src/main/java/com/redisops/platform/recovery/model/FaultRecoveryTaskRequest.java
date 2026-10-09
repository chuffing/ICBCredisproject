package com.redisops.platform.recovery.model;

import java.util.List;

/**
 * 创建三节点 Redis + Sentinel 故障恢复任务的业务请求。
 */
public class FaultRecoveryTaskRequest {

    private String createdBy;
    private String operationReason;
    private String masterExecutorId;
    private Integer redisPort;
    private List<FaultRecoveryNodeRequest> nodes;

    /** 获取任务创建人。 */
    public String getCreatedBy() {
        return createdBy;
    }

    /** 设置任务创建人。 */
    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    /** 获取本次故障恢复原因。 */
    public String getOperationReason() {
        return operationReason;
    }

    /** 设置本次故障恢复原因。 */
    public void setOperationReason(String operationReason) {
        this.operationReason = operationReason;
    }

    /** 获取备份配置中指定为主 Redis 的执行器编号。 */
    public String getMasterExecutorId() {
        return masterExecutorId;
    }

    /** 设置备份配置中指定为主 Redis 的执行器编号。 */
    public void setMasterExecutorId(String masterExecutorId) {
        this.masterExecutorId = masterExecutorId;
    }

    /** 获取 Redis 服务端口；不传时使用 6379。 */
    public Integer getRedisPort() {
        return redisPort;
    }

    /** 设置 Redis 服务端口。 */
    public void setRedisPort(Integer redisPort) {
        this.redisPort = redisPort;
    }

    /** 获取需要恢复的三台服务器。 */
    public List<FaultRecoveryNodeRequest> getNodes() {
        return nodes;
    }

    /** 设置需要恢复的三台服务器。 */
    public void setNodes(List<FaultRecoveryNodeRequest> nodes) {
        this.nodes = nodes;
    }
}
