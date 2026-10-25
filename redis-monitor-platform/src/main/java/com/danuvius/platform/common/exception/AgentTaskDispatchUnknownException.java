package com.danuvius.platform.common.exception;

/**
 * 平台无法确认执行器是否收到运维任务。
 */
public class AgentTaskDispatchUnknownException extends RuntimeException {

    /**
     * 创建执行器任务下发结果未知异常。
     *
     * @param message 异常说明
     * @param cause 原始异常
     */
    public AgentTaskDispatchUnknownException(
            String message,
            Throwable cause) {
        super(message, cause);
    }

    /**
     * 创建执行器任务下发结果未知异常。
     *
     * @param message 异常说明
     */
    public AgentTaskDispatchUnknownException(String message) {
        super(message);
    }
}
