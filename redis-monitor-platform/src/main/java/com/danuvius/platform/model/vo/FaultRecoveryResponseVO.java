package com.danuvius.platform.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 故障恢复响应。
 */
@Data
@AllArgsConstructor
public class FaultRecoveryResponseVO {

    /**
     * 故障恢复任务编号。
     */
    private String taskNo;

    /**
     * 故障恢复任务状态。
     */
    private String taskStatus;

    /**
     * 故障恢复任务当前状态说明。
     */
    private String message;

    /**
     * 故障恢复失败或结果未知的具体原因。
     */
    private String failureReason;

    /** 人工结束原因。 */
    private String terminationReason;

    /** 人工结束操作人。 */
    private String terminatedBy;

    /** 人工结束时间。 */
    private LocalDateTime terminatedTime;
}
