package com.danuvius.platform.model.enums;

/**
 * 运维任务步骤状态。
 */
public enum OperationTaskStepStatusEnum {

    /** 步骤已创建，尚未下发。 */
    CREATED,

    /** 步骤已经进入下发流程，但尚未确认执行器是否接收。 */
    DISPATCHING,

    /** 步骤已下发，正在等待执行结果。 */
    DISPATCHED,

    /** 步骤执行结果暂时无法确认，需要等待补报或人工处理。 */
    RESULT_UNKNOWN,

    /** 步骤执行成功。 */
    SUCCESS,

    /** 步骤执行失败。 */
    FAILED
}
