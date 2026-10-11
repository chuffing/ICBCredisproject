package com.redisops.platform.operation.entity;

import lombok.Data;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * operation_task_step 表对应的执行器原子操作步骤。
 */
@TableName("operation_task_step")
@Data
public class OperationTaskStep {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String taskNo;
    private Integer stepNo;
    private String executorId;
    private String operationType;
    private String stepStatus;
    private String requestParams;
    private String resultJson;
    private String errorCode;
    private String failureReason;
    private Integer dispatchCount;
    private LocalDateTime lastDispatchTime;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
