package com.danuvius.platform.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

/**
 * 执行器向平台上报的任务执行结果。
 */
@Data
public class TaskResultReportDTO {

    /**
     * 任务编号。
     */
    private String taskNo;

    /**
     * 步骤编号。
     */
    private Integer stepNo;

    /**
     * 平台根据Bearer Token查询并回填的执行器编号，执行器不需要上报。
     */
    @JsonIgnore
    private String agentId;

    /**
     * 运维操作类型。
     */
    private String operationType;

    /**
     * 当前任务步骤的执行结果状态，只能为SUCCESS或FAILED。
     */
    private String status;

    /**
     * OperationResultDTO序列化后的完整执行结果JSON。
     */
    private String result;
}

