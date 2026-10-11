package com.redisops.platform.operation.model;

import lombok.Data;

/**
 * 执行器完成一个原子操作后上报的结果。
 */
@Data
public class TaskResultReportRequest {

    private String taskNo;
    private Integer stepNo;
    private String agentId;
    private String operationType;
    private String status;
    private String result;
}
