package com.redisops.platform.operation.model;

/**
 * 执行器完成一个原子操作后上报的结果。
 */
public class TaskResultReportRequest {

    private String taskNo;
    private Integer stepNo;
    private String agentId;
    private String operationType;
    private String status;
    private String result;

    /** 获取任务编号。 */ public String getTaskNo() { return taskNo; }
    /** 设置任务编号。 */ public void setTaskNo(String taskNo) { this.taskNo = taskNo; }
    /** 获取步骤序号。 */ public Integer getStepNo() { return stepNo; }
    /** 设置步骤序号。 */ public void setStepNo(Integer stepNo) { this.stepNo = stepNo; }
    /** 获取执行器编号。 */ public String getAgentId() { return agentId; }
    /** 设置执行器编号。 */ public void setAgentId(String agentId) { this.agentId = agentId; }
    /** 获取原子操作类型。 */ public String getOperationType() { return operationType; }
    /** 设置原子操作类型。 */ public void setOperationType(String operationType) { this.operationType = operationType; }
    /** 获取执行状态。 */ public String getStatus() { return status; }
    /** 设置执行状态。 */ public void setStatus(String status) { this.status = status; }
    /** 获取执行结果 JSON。 */ public String getResult() { return result; }
    /** 设置执行结果 JSON。 */ public void setResult(String result) { this.result = result; }
}
