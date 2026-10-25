package com.danuvius.platform.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 运维任务执行步骤。
 */
@Data
@TableName("operation_task_step")
public class OperationTaskStep {

    /** 内部主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属运维任务编号。 */
    private String taskNo;

    /** 阶段编号，同一阶段可以包含多台执行器的步骤。 */
    private Integer stepNo;

    /** 目标执行器编号。 */
    private String executorId;

    /** 执行器原子操作类型。 */
    private String operationType;

    /** 步骤状态。 */
    private String stepStatus;

    /** 下发给执行器的任务参数JSON。 */
    private String requestParams;

    /** 执行器返回的完整结果JSON。 */
    private String resultJson;

    /** 执行器返回的错误码。 */
    private String errorCode;

    /** 步骤失败原因。 */
    private String failureReason;

    /** 任务下发时间。 */
    private LocalDateTime dispatchTime;

    /** 步骤开始时间。 */
    private LocalDateTime startTime;

    /** 步骤结束时间。 */
    private LocalDateTime endTime;

    /** 创建时间。 */
    private LocalDateTime createdTime;

    /** 更新时间。 */
    private LocalDateTime updatedTime;
}
