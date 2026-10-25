package com.danuvius.platform.model.dto;

import lombok.Data;

/**
 * 人工结束运维任务请求。
 */
@Data
public class ManualTerminateTaskRequestDTO {

    /** 人工结束原因。 */
    private String terminationReason;

    /** 人工结束操作人。 */
    private String terminatedBy;
}
