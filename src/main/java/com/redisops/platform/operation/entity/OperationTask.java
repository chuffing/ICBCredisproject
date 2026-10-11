package com.redisops.platform.operation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * operation_task 表对应的一条完整运维业务任务。
 */
@TableName("operation_task")
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

    /** 获取主键。 */ public Long getId() { return id; }
    /** 设置主键。 */ public void setId(Long id) { this.id = id; }
    /** 获取任务编号。 */ public String getTaskNo() { return taskNo; }
    /** 设置任务编号。 */ public void setTaskNo(String taskNo) { this.taskNo = taskNo; }
    /** 获取业务任务类型。 */ public String getTaskType() { return taskType; }
    /** 设置业务任务类型。 */ public void setTaskType(String taskType) { this.taskType = taskType; }
    /** 获取任务状态。 */ public String getTaskStatus() { return taskStatus; }
    /** 设置任务状态。 */ public void setTaskStatus(String taskStatus) { this.taskStatus = taskStatus; }
    /** 获取任务参数 JSON。 */ public String getRequestParams() { return requestParams; }
    /** 设置任务参数 JSON。 */ public void setRequestParams(String requestParams) { this.requestParams = requestParams; }
    /** 获取失败原因。 */ public String getFailureReason() { return failureReason; }
    /** 设置失败原因。 */ public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
    /** 获取创建人。 */ public String getCreatedBy() { return createdBy; }
    /** 设置创建人。 */ public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    /** 获取运维原因。 */ public String getOperationReason() { return operationReason; }
    /** 设置运维原因。 */ public void setOperationReason(String operationReason) { this.operationReason = operationReason; }
    /** 获取开始时间。 */ public LocalDateTime getStartTime() { return startTime; }
    /** 设置开始时间。 */ public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
    /** 获取结束时间。 */ public LocalDateTime getEndTime() { return endTime; }
    /** 设置结束时间。 */ public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
    /** 获取创建时间。 */ public LocalDateTime getCreateTime() { return createTime; }
    /** 设置创建时间。 */ public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    /** 获取更新时间。 */ public LocalDateTime getUpdateTime() { return updateTime; }
    /** 设置更新时间。 */ public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
