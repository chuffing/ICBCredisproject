package com.danuvius.platform.common.constant;

/**
 * 运维任务常量。
 */
public final class OperationTaskConstants {

    /** 工具类不允许实例化。 */
    private OperationTaskConstants() {
    }

    /** 运维任务编号前缀。 */
    public static final String TASK_NO_PREFIX = "TASK";

    /** 每天允许生成的最大任务流水号。 */
    public static final int MAX_DAILY_TASK_SEQUENCE = 9999;

}
