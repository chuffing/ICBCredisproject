package com.redisops.platform.operation.model;

import java.util.Map;

/**
 * 平台发送给执行器的原子操作请求。
 */
public class AgentTaskRequest {

    private String taskNo;
    private Integer stepNo;
    private String taskType;
    private Map<String, String> params;

    /** 获取任务编号。 */ public String getTaskNo() { return taskNo; }
    /** 设置任务编号。 */ public void setTaskNo(String taskNo) { this.taskNo = taskNo; }
    /** 获取步骤序号。 */ public Integer getStepNo() { return stepNo; }
    /** 设置步骤序号。 */ public void setStepNo(Integer stepNo) { this.stepNo = stepNo; }
    /** 获取执行器任务类型。 */ public String getTaskType() { return taskType; }
    /** 设置执行器任务类型。 */ public void setTaskType(String taskType) { this.taskType = taskType; }
    /** 获取操作参数。 */ public Map<String, String> getParams() { return params; }
    /** 设置操作参数。 */ public void setParams(Map<String, String> params) { this.params = params; }
}
