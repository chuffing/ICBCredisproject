package com.redisops.platform.operation.model;

import lombok.Data;
import java.util.Map;

/**
 * 平台发送给执行器的原子操作请求。
 */
@Data
public class AgentTaskRequest {

    private String taskNo;
    private Integer stepNo;
    private String taskType;
    private Map<String, String> params;
}
