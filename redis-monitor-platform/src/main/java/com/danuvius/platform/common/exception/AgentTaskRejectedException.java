package com.danuvius.platform.common.exception;

/**
 * 执行器明确拒绝接收运维任务。
 */
public class AgentTaskRejectedException extends RuntimeException {

    /**
     * 创建执行器任务拒绝异常。
     *
     * @param message 异常说明
     */
    public AgentTaskRejectedException(String message) {
        super(message);
    }
}
