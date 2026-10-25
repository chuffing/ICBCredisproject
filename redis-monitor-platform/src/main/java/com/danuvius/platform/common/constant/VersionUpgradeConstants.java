package com.danuvius.platform.common.constant;

/**
 * Redis版本升级业务常量。
 */
public final class VersionUpgradeConstants {

    private VersionUpgradeConstants() {
    }

    public static final int DEPLOY_VERSION_STEP_NO = 1;
    public static final int STOP_SENTINEL_STEP_NO = 2;
    public static final int STOP_REDIS_STEP_NO = 3;
    public static final int RESTORE_CONFIG_STEP_NO = 4;
    public static final int SWITCH_VERSION_STEP_NO = 5;
    public static final int START_REDIS_STEP_NO = 6;
    public static final int START_SENTINEL_STEP_NO = 7;
    public static final int VERIFY_TOPOLOGY_STEP_NO = 8;

    public static final String PACKAGE_ID_PARAM = "packageId";
    public static final String TARGET_VERSION_PARAM = "targetVersion";
    public static final String PACKAGE_SHA256_PARAM = "packageSha256";
    public static final String NODES_PARAM = "nodes";
    public static final String REDIS_INSTANCE_ID_PARAM = "redisInstanceId";

    public static final String TASK_CREATED_MESSAGE = "版本升级任务已创建";
    public static final String TASK_RUNNING_MESSAGE = "版本升级正在执行";
    public static final String TASK_RESULT_UNKNOWN_MESSAGE =
            "版本升级结果暂时无法确认，请人工检查";
    public static final String TASK_MANUALLY_TERMINATED_MESSAGE =
            "版本升级任务已人工结束";
    public static final String TASK_SUCCESS_MESSAGE = "版本升级成功";
    public static final String TASK_FAILED_MESSAGE = "版本升级失败";
}
