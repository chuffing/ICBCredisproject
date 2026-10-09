package com.redisops.platform.operation.model;

import java.util.Map;

/**
 * 创建任务时描述一台执行器上的一个原子操作。
 */
public class CreateOperationTaskStepRequest {

    private Integer stepNo;
    private String executorId;
    private String operationType;
    private Map<String, String> params;

    /** 获取步骤序号。 */ public Integer getStepNo() { return stepNo; }
    /** 设置步骤序号。 */ public void setStepNo(Integer stepNo) { this.stepNo = stepNo; }
    /** 获取执行器编号。 */ public String getExecutorId() { return executorId; }
    /** 设置执行器编号。 */ public void setExecutorId(String executorId) { this.executorId = executorId; }
    /** 获取原子操作类型。 */ public String getOperationType() { return operationType; }
    /** 设置原子操作类型。 */ public void setOperationType(String operationType) { this.operationType = operationType; }
    /** 获取操作参数。 */ public Map<String, String> getParams() { return params; }
    /** 设置操作参数。 */ public void setParams(Map<String, String> params) { this.params = params; }
}
