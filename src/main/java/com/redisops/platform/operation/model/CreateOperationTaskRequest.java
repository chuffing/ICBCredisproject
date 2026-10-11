package com.redisops.platform.operation.model;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 创建一条运维业务任务及其执行计划的请求。
 */
@Data
public class CreateOperationTaskRequest {

    /** 任务类型。 */
    private String taskType;

    /** 创建人。 */
    private String createdBy;

    /** 运维原因。 */
    private String operationReason;

    /** 业务参数。 */
    private Map<String, Object> requestParams;

    /** 步骤计划。 */
    private List<CreateOperationTaskStepRequest> steps;

}
