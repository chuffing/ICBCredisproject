package com.danuvius.platform.common.constant;

/**
 * 服务器切换业务常量。
 */
public final class ServerSwitchConstants {

    private ServerSwitchConstants() {
    }

    public static final int TARGET_NODE_COUNT = 3;
    public static final int REPLICA_NODE_COUNT = 2;
    public static final long MAX_CONFIG_FILE_SIZE_BYTES = 1024L * 1024L;

    public static final String REDIS_CONFIG_FILE_NAME = "redis.conf";
    public static final String SENTINEL_CONFIG_FILE_NAME = "sentinel.conf";

    public static final int PRECHECK_HOST_STEP_NO = 1;
    public static final int DEPLOY_VERSION_STEP_NO = 2;
    public static final int STOP_TARGET_SENTINEL_STEP_NO = 3;
    public static final int STOP_TARGET_REDIS_STEP_NO = 4;
    public static final int DEPLOY_CONFIG_STEP_NO = 5;
    public static final int SWITCH_VERSION_STEP_NO = 6;
    public static final int START_MASTER_REDIS_STEP_NO = 7;
    public static final int START_REPLICA_REDIS_STEP_NO = 8;
    public static final int START_TARGET_SENTINEL_STEP_NO = 9;
    public static final int STOP_SOURCE_SENTINEL_STEP_NO = 10;
    public static final int STOP_SOURCE_REDIS_STEP_NO = 11;

    public static final String PACKAGE_ID_PARAM = "packageId";
    public static final String TARGET_VERSION_PARAM = "targetVersion";
    public static final String PACKAGE_SHA256_PARAM = "packageSha256";
    public static final String REDIS_CONFIG_SHA256_PARAM =
            "redisConfigSha256";
    public static final String SENTINEL_CONFIG_SHA256_PARAM =
            "sentinelConfigSha256";

    public static final String MASTER_ROLE = "MASTER";
    public static final String REPLICA_ROLE = "REPLICA";

    public static final String TASK_CREATED_MESSAGE = "服务器切换任务已创建";
    public static final String TASK_RUNNING_MESSAGE = "服务器切换正在执行";
    public static final String TASK_RESULT_UNKNOWN_MESSAGE =
            "服务器切换结果暂时无法确认，请人工检查";
    public static final String TASK_MANUALLY_TERMINATED_MESSAGE =
            "服务器切换任务已人工结束";
    public static final String TASK_SUCCESS_MESSAGE = "服务器切换成功";
    public static final String TASK_FAILED_MESSAGE = "服务器切换失败";
}
