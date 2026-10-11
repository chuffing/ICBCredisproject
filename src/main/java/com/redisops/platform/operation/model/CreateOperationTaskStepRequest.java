package com.redisops.platform.operation.model;

import lombok.Data;
import java.util.Map;

/**
 * 创建任务时描述一台执行器上的一个原子操作。
 */
@Data
public class CreateOperationTaskStepRequest {

    private Integer stepNo;
    private String executorId;
    private String operationType;
    private Map<String, String> params;
}
