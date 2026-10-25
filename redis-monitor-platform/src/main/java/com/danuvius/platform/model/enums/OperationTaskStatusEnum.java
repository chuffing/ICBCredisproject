package com.danuvius.platform.model.enums;

/**
 * 运维任务状态。
 */
public enum OperationTaskStatusEnum {

    /** 任务已创建。 */
    CREATED,

    /** 任务执行中。 */
    RUNNING,

    /** 任务执行结果暂时无法确认，需要等待补报或人工处理。 */
    RESULT_UNKNOWN,

    /** 运维人员确认现场状态后人工结束任务。 */
    MANUALLY_TERMINATED,

    /** 任务执行成功。 */
    SUCCESS,

    /** 任务执行失败。 */
    FAILED
}
