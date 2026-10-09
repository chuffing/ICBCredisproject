package com.redisops.platform.serverswitch.model;

import java.util.List;

/**
 * 创建三节点 Redis + Sentinel 服务器切换任务的业务参数。
 */
public class ServerSwitchTaskRequest {

    private Long versionPackageId;
    private String masterHostIp;
    private Integer redisPort;
    private List<ServerSwitchNodeRequest> nodes;
    private String createdBy;
    private String operationReason;

    /** 获取准备部署到新服务器的版本包主键。 */
    public Long getVersionPackageId() { return versionPackageId; }

    /** 设置准备部署到新服务器的版本包主键。 */
    public void setVersionPackageId(Long value) { this.versionPackageId = value; }

    /** 获取用户指定的新拓扑主节点IP。 */
    public String getMasterHostIp() { return masterHostIp; }

    /** 设置用户指定的新拓扑主节点IP。 */
    public void setMasterHostIp(String value) { this.masterHostIp = value; }

    /** 获取 Redis 端口。 */
    public Integer getRedisPort() { return redisPort; }

    /** 设置 Redis 端口。 */
    public void setRedisPort(Integer value) { this.redisPort = value; }

    /** 获取三台新服务器。 */
    public List<ServerSwitchNodeRequest> getNodes() { return nodes; }

    /** 设置三台新服务器。 */
    public void setNodes(List<ServerSwitchNodeRequest> value) { this.nodes = value; }

    /** 获取任务创建人。 */
    public String getCreatedBy() { return createdBy; }

    /** 设置任务创建人。 */
    public void setCreatedBy(String value) { this.createdBy = value; }

    /** 获取服务器切换原因。 */
    public String getOperationReason() { return operationReason; }

    /** 设置服务器切换原因。 */
    public void setOperationReason(String value) { this.operationReason = value; }
}
