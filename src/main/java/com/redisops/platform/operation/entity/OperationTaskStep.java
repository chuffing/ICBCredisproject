package com.redisops.platform.operation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * operation_task_step 表对应的执行器原子操作步骤。
 */
@TableName("operation_task_step")
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

    /** 获取主键。 */ public Long getId() { return id; }
    /** 设置主键。 */ public void setId(Long id) { this.id = id; }
    /** 获取任务编号。 */ public String getTaskNo() { return taskNo; }
    /** 设置任务编号。 */ public void setTaskNo(String taskNo) { this.taskNo = taskNo; }
    /** 获取步骤序号。 */ public Integer getStepNo() { return stepNo; }
    /** 设置步骤序号。 */ public void setStepNo(Integer stepNo) { this.stepNo = stepNo; }
    /** 获取执行器编号。 */ public String getExecutorId() { return executorId; }
    /** 设置执行器编号。 */ public void setExecutorId(String executorId) { this.executorId = executorId; }
    /** 获取原子操作类型。 */ public String getOperationType() { return operationType; }
    /** 设置原子操作类型。 */ public void setOperationType(String operationType) { this.operationType = operationType; }
    /** 获取步骤状态。 */ public String getStepStatus() { return stepStatus; }
    /** 设置步骤状态。 */ public void setStepStatus(String stepStatus) { this.stepStatus = stepStatus; }
    /** 获取下发参数 JSON。 */ public String getRequestParams() { return requestParams; }
    /** 设置下发参数 JSON。 */ public void setRequestParams(String requestParams) { this.requestParams = requestParams; }
    /** 获取执行结果 JSON。 */ public String getResultJson() { return resultJson; }
    /** 设置执行结果 JSON。 */ public void setResultJson(String resultJson) { this.resultJson = resultJson; }
    /** 获取错误码。 */ public String getErrorCode() { return errorCode; }
    /** 设置错误码。 */ public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    /** 获取失败原因。 */ public String getFailureReason() { return failureReason; }
    /** 设置失败原因。 */ public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
    /** 获取下发次数。 */ public Integer getDispatchCount() { return dispatchCount; }
    /** 设置下发次数。 */ public void setDispatchCount(Integer dispatchCount) { this.dispatchCount = dispatchCount; }
    /** 获取最近下发时间。 */ public LocalDateTime getLastDispatchTime() { return lastDispatchTime; }
    /** 设置最近下发时间。 */ public void setLastDispatchTime(LocalDateTime lastDispatchTime) { this.lastDispatchTime = lastDispatchTime; }
    /** 获取开始时间。 */ public LocalDateTime getStartTime() { return startTime; }
    /** 设置开始时间。 */ public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
    /** 获取结束时间。 */ public LocalDateTime getEndTime() { return endTime; }
    /** 设置结束时间。 */ public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
    /** 获取创建时间。 */ public LocalDateTime getCreateTime() { return createTime; }
    /** 设置创建时间。 */ public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    /** 获取更新时间。 */ public LocalDateTime getUpdateTime() { return updateTime; }
    /** 设置更新时间。 */ public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
