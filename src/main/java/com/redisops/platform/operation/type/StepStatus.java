package com.redisops.platform.operation.type;

/** 单个执行步骤的状态。 */
public enum StepStatus {
    CREATED,        // 已创建，尚未下发
    DISPATCHED,     // 已下发，等待结果
    SUCCESS,        // 执行成功
    FAILED,         // 执行失败
    CANCELLED,      // 已取消
    RESULT_UNKNOWN  // 结果暂时无法确认
}
