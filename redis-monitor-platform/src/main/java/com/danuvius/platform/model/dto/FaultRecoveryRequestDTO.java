package com.danuvius.platform.model.dto;

import lombok.Data;

/**
 * 故障恢复请求。
 */
@Data
public class FaultRecoveryRequestDTO {

    /**
     * 本次执行故障恢复的操作原因。
     */
    private String operationReason;

    /**
     * 本次执行故障恢复的操作人。
     */
    private String createdBy;
}
