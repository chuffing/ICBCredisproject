package com.redisops.platform.recovery.model;

import lombok.Data;

import java.util.List;

/**
 * 创建三节点 Redis + Sentinel 故障恢复任务的业务请求。
 */
@Data
public class FaultRecoveryTaskRequest {

    /** 任务创建人。 */
    private String createdBy;
    /** 本次故障恢复原因。 */
    private String operationReason;
    /** 备份配置中指定为主 Redis 的执行器编号。 */
    private String masterExecutorId;
    /** redis服务端口。 */
    private Integer redisPort;
    /** 需要恢复的三台服务器 */
    private List<FaultRecoveryNodeRequest> nodes;
}
