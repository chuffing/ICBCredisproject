package com.redisops.platform.serverswitch.model;

import lombok.Data;
import java.util.List;

/**
 * 创建三节点 Redis + Sentinel 服务器切换任务的业务参数。
 */
@Data
public class ServerSwitchTaskRequest {

    private Long versionPackageId;
    private String masterHostIp;
    private Integer redisPort;
    private List<ServerSwitchNodeRequest> nodes;
    private String createdBy;
    private String operationReason;
}
