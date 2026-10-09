package com.redisops.platform.versionupgrade.model;

import java.util.List;

/**
 * 创建三节点 Redis + Sentinel 版本升级任务的业务参数。
 */
public class VersionUpgradeTaskRequest {

    private Long versionPackageId;
    private String masterExecutorId;
    private Integer redisPort;
    private List<VersionUpgradeNodeRequest> nodes;
    private String createdBy;
    private String operationReason;

    /** 获取平台版本包主键。 */ public Long getVersionPackageId() { return versionPackageId; }
    /** 设置平台版本包主键。 */ public void setVersionPackageId(Long versionPackageId) { this.versionPackageId = versionPackageId; }
    /** 获取当前拓扑主节点的执行器编号。 */ public String getMasterExecutorId() { return masterExecutorId; }
    /** 设置当前拓扑主节点的执行器编号。 */ public void setMasterExecutorId(String masterExecutorId) { this.masterExecutorId = masterExecutorId; }
    /** 获取 Redis 端口。 */ public Integer getRedisPort() { return redisPort; }
    /** 设置 Redis 端口。 */ public void setRedisPort(Integer redisPort) { this.redisPort = redisPort; }
    /** 获取参与升级的三台节点。 */ public List<VersionUpgradeNodeRequest> getNodes() { return nodes; }
    /** 设置参与升级的三台节点。 */ public void setNodes(List<VersionUpgradeNodeRequest> nodes) { this.nodes = nodes; }
    /** 获取任务创建人。 */ public String getCreatedBy() { return createdBy; }
    /** 设置任务创建人。 */ public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    /** 获取升级原因。 */ public String getOperationReason() { return operationReason; }
    /** 设置升级原因。 */ public void setOperationReason(String operationReason) { this.operationReason = operationReason; }
}
