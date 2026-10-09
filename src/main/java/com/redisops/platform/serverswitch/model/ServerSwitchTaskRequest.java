package com.redisops.platform.serverswitch.model;

import java.util.List;

/**
 * 创建三节点 Redis + Sentinel 服务器切换任务的业务参数。
 */
public class ServerSwitchTaskRequest {

    private Long versionPackageId;
    private String masterExecutorId;
    private Integer redisPort;
    private List<ServerSwitchNodeRequest> nodes;
    private List<ServerSwitchNodeRequest> sourceNodes;
    private String createdBy;
    private String operationReason;

    /** 获取准备部署到新服务器的版本包主键。 */
    public Long getVersionPackageId() { return versionPackageId; }

    /** 设置准备部署到新服务器的版本包主键。 */
    public void setVersionPackageId(Long value) { this.versionPackageId = value; }

    /** 获取新拓扑主节点的执行器编号。 */
    public String getMasterExecutorId() { return masterExecutorId; }

    /** 设置新拓扑主节点的执行器编号。 */
    public void setMasterExecutorId(String value) { this.masterExecutorId = value; }

    /** 获取 Redis 端口。 */
    public Integer getRedisPort() { return redisPort; }

    /** 设置 Redis 端口。 */
    public void setRedisPort(Integer value) { this.redisPort = value; }

    /** 获取三台新服务器。 */
    public List<ServerSwitchNodeRequest> getNodes() { return nodes; }

    /** 设置三台新服务器。 */
    public void setNodes(List<ServerSwitchNodeRequest> value) { this.nodes = value; }

    /** 获取当前旧拓扑的三台服务器。 */
    public List<ServerSwitchNodeRequest> getSourceNodes() { return sourceNodes; }

    /** 设置当前旧拓扑的三台服务器。 */
    public void setSourceNodes(List<ServerSwitchNodeRequest> value) { sourceNodes = value; }

    /** 获取任务创建人。 */
    public String getCreatedBy() { return createdBy; }

    /** 设置任务创建人。 */
    public void setCreatedBy(String value) { this.createdBy = value; }

    /** 获取服务器切换原因。 */
    public String getOperationReason() { return operationReason; }

    /** 设置服务器切换原因。 */
    public void setOperationReason(String value) { this.operationReason = value; }
}
