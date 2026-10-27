package com.danuvius.platform.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 运维任务。
 */
@Data
@TableName("operation_task")
public class OperationTask {

    /** 内部主键。 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 运维任务编号。 */
    private String taskNo;

    /** 运维任务类型。 */
    private String taskType;

    /** 运维任务状态。 */
    private String taskStatus;

    /** 操作原因。 */
    private String operationReason;

    /** 操作人。 */
    private String createdBy;

    /** 创建任务时保存的前端请求参数JSON。 */
    private String requestParams;

    /** 任务失败原因。 */
    private String failureReason;

    /** 人工结束原因。 */
    private String terminationReason;

    /** 人工结束操作人。 */
    private String terminatedBy;

    /** 人工结束时间。 */
    private LocalDateTime terminatedTime;

    /** 任务开始时间。 */
    private LocalDateTime startTime;

    /** 任务结束时间。 */
    private LocalDateTime endTime;

    /** 创建时间。 */
    private LocalDateTime createdTime;

    /** 更新时间。 */
    private LocalDateTime updatedTime;
}
