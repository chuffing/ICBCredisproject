package com.danuvius.platform.model.enums;

/**
 * 执行器接收运维任务后的状态。
 */
public enum AgentTaskSubmitStatusEnum {

    /** 任务已接收。 */
    ACCEPTED,

    /** 执行器繁忙，任务未接收。 */
    BUSY
}
