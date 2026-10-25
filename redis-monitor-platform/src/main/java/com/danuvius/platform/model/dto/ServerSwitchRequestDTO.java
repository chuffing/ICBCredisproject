package com.danuvius.platform.model.dto;

import lombok.Data;

import java.util.List;

/**
 * Redis服务器切换请求。
 */
@Data
public class ServerSwitchRequestDTO {

    /** 目标主节点服务器IP。 */
    private String masterHostIp;

    /** 两个目标从节点服务器IP，列表顺序不影响任务执行。 */
    private List<String> replicaHostIps;

    /** 目标Redis版本安装包记录编号。 */
    private Long packageId;

    /** 本次服务器切换的操作原因。 */
    private String operationReason;

    /** 本次服务器切换的操作人。 */
    private String createdBy;
}
