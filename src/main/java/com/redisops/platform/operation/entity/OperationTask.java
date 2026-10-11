package com.redisops.platform.operation.entity;

import lombok.Data;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * operation_task 表对应的一条完整运维业务任务。
 */
@TableName("operation_task")
@Data
public class OperationTask{

    @TableId(type = IdType.AUTO)
    private Long id;
    private String taskNo;
    private String taskType;
    private String taskStatus;
    private String requestParams;
    private String failureReason;
    private String createdBy;
    private String operationReason;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
