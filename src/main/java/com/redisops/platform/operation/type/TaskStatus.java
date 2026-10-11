package com.redisops.platform.operation.type;

/** 一条完整运维任务的状态。 */
public enum TaskStatus {
    CREATED,       // 已创建
    RUNNING,       // 执行中
    SUCCESS,       // 执行成功
    FAILED,        // 执行失败
    ROLLING_BACK,  // 回滚中
    ROLLED_BACK    // 已回滚
}
