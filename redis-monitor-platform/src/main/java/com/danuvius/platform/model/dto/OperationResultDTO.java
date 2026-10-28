package com.danuvius.platform.model.dto;

import lombok.Data;

/**
 * 执行器返回的运维操作结果。
 */
@Data
public class OperationResultDTO {

    /** 操作是否执行成功。 */
    private Boolean success;

    /** 操作失败错误码，成功时为空。 */
    private Integer errorCode;

    /** 操作结果说明。 */
    private String message;

    /** 操作开始时间，使用毫秒时间戳。 */
    private Long startTime;

    /** 操作结束时间，使用毫秒时间戳。 */
    private Long endTime;
}
