package com.danuvius.platform.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Redis服务器切换响应。
 */
@Data
@AllArgsConstructor
public class ServerSwitchResponseVO {

    /** 服务器切换任务编号。 */
    private String taskNo;

    /** 服务器切换任务状态。 */
    private String taskStatus;

    /** 本次切换使用的目标Redis版本。 */
    private String targetVersion;

    /** 三台目标服务器中的Redis主节点IP。 */
    private String masterHostIp;

    /** 服务器切换任务当前状态说明。 */
    private String message;

    /** 服务器切换失败或结果未知的具体原因。 */
    private String failureReason;

    /** 人工结束原因。 */
    private String terminationReason;

    /** 人工结束操作人。 */
    private String terminatedBy;

    /** 人工结束时间。 */
    private LocalDateTime terminatedTime;
}
