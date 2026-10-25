package com.danuvius.platform.model.dto;

import lombok.Data;

import java.util.Map;

/**
 * 平台下发给执行器的运维任务。
 */
@Data
public class AgentTaskRequestDTO {

    /** 所属运维任务编号。 */
    private String taskNo;

    /** 当前任务阶段编号。 */
    private Integer stepNo;

    /** 要求执行器完成的原子操作类型。 */
    private String operationType;

    /** 当前原子操作需要的参数。 */
    private Map<String, String> params;
}
