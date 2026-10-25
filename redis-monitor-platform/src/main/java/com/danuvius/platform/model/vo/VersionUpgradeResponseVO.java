package com.danuvius.platform.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Redis版本升级响应。
 */
@Data
@AllArgsConstructor
public class VersionUpgradeResponseVO {

    /** 版本升级任务编号。 */
    private String taskNo;

    /** 版本升级任务状态。 */
    private String taskStatus;

    /** 本次升级的目标Redis版本。 */
    private String targetVersion;

    /** 版本升级任务当前状态说明。 */
    private String message;

    /** 版本升级失败或结果未知的具体原因。 */
    private String failureReason;

    /** 人工结束原因。 */
    private String terminationReason;

    /** 人工结束操作人。 */
    private String terminatedBy;

    /** 人工结束时间。 */
    private LocalDateTime terminatedTime;
}
