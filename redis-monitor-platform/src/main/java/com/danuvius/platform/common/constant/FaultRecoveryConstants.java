package com.danuvius.platform.common.constant;

/**
 * 故障恢复业务常量。
 */
public final class FaultRecoveryConstants {

    /**
     * 工具类不允许实例化。
     */
    private FaultRecoveryConstants() {
    }

    /** 停止Sentinel阶段编号。 */
    public static final int STOP_SENTINEL_STEP_NO = 1;

    /** 停止Redis阶段编号。 */
    public static final int STOP_REDIS_STEP_NO = 2;

    /** 恢复配置阶段编号。 */
    public static final int RESTORE_CONFIG_STEP_NO = 3;

    /** 启动Redis阶段编号。 */
    public static final int START_REDIS_STEP_NO = 4;

    /** 启动Sentinel阶段编号。 */
    public static final int START_SENTINEL_STEP_NO = 5;

    /** Redis备份配置文件SHA256任务参数名称。 */
    public static final String REDIS_CONFIG_SHA256_PARAM =
            "redisConfigSha256";

    /** Sentinel备份配置文件SHA256任务参数名称。 */
    public static final String SENTINEL_CONFIG_SHA256_PARAM =
            "sentinelConfigSha256";

    /** 故障恢复任务创建成功提示。 */
    public static final String TASK_CREATED_MESSAGE = "故障恢复任务已创建";

    /** 故障恢复任务执行中提示。 */
    public static final String TASK_RUNNING_MESSAGE = "故障恢复正在执行";

    /** 故障恢复任务结果未知提示。 */
    public static final String TASK_RESULT_UNKNOWN_MESSAGE =
            "故障恢复结果暂时无法确认，请人工检查";

    /** 故障恢复任务已人工结束提示。 */
    public static final String TASK_MANUALLY_TERMINATED_MESSAGE =
            "故障恢复任务已人工结束";

    /** 故障恢复任务成功提示。 */
    public static final String TASK_SUCCESS_MESSAGE = "故障恢复成功";

    /** 故障恢复任务失败提示。 */
    public static final String TASK_FAILED_MESSAGE = "故障恢复失败";

}
