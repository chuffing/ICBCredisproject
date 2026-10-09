package com.redisops.platform.operation.model;

import java.util.List;
import java.util.Map;

/**
 * 创建一条运维业务任务及其执行计划的请求。
 */
public class CreateOperationTaskRequest {

    private String taskType;
    private String createdBy;
    private String operationReason;
    private Map<String, Object> requestParams;
    private List<CreateOperationTaskStepRequest> steps;

    /** 获取任务类型。 */ public String getTaskType() { return taskType; }
    /** 设置任务类型。 */ public void setTaskType(String taskType) { this.taskType = taskType; }
    /** 获取创建人。 */ public String getCreatedBy() { return createdBy; }
    /** 设置创建人。 */ public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    /** 获取运维原因。 */ public String getOperationReason() { return operationReason; }
    /** 设置运维原因。 */ public void setOperationReason(String operationReason) { this.operationReason = operationReason; }
    /** 获取业务参数。 */ public Map<String, Object> getRequestParams() { return requestParams; }
    /** 设置业务参数。 */ public void setRequestParams(Map<String, Object> requestParams) { this.requestParams = requestParams; }
    /** 获取步骤计划。 */ public List<CreateOperationTaskStepRequest> getSteps() { return steps; }
    /** 设置步骤计划。 */ public void setSteps(List<CreateOperationTaskStepRequest> steps) { this.steps = steps; }
}
