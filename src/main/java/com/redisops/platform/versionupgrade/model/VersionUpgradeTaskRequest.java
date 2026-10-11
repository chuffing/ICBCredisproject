package com.redisops.platform.versionupgrade.model;

import lombok.Data;
import java.util.List;

/**
 * 创建三节点 Redis + Sentinel 版本升级任务的业务参数。
 */
@Data
public class VersionUpgradeTaskRequest {

    private Long versionPackageId;
    private String masterExecutorId;
    private Integer redisPort;
    private List<VersionUpgradeNodeRequest> nodes;
    private String createdBy;
    private String operationReason;
}
