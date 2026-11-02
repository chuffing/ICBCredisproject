package com.danuvius.platform.common.constant;

/**
 * 平台向执行器下发任务时使用的通信常量。
 */
public final class AgentTaskConstants {

    /** 执行器普通运维任务接口路径。 */
    public static final String TASK_PATH = "/api/agent/task";

    /** 执行器Redis源码包上传接口路径。 */
    public static final String VERSION_DEPLOY_PATH =
            "/api/agent/version/deploy";

    /** 执行器Redis和Sentinel配置文件上传接口路径。 */
    public static final String CONFIG_DEPLOY_PATH =
            "/api/agent/config/deploy";

    /** HTTP认证请求头。 */
    public static final String AUTHORIZATION_HEADER = "Authorization";

    /** Bearer认证前缀。 */
    public static final String BEARER_PREFIX = "Bearer ";

    /** multipart中的任务报文部分名称。 */
    public static final String TASK_PART_NAME = "task";

    /** multipart中的版本安装包部分名称。 */
    public static final String VERSION_FILE_PART_NAME = "file";

    /** multipart中的Redis配置文件部分名称。 */
    public static final String REDIS_CONFIG_PART_NAME = "redisFile";

    /** multipart中的Sentinel配置文件部分名称。 */
    public static final String SENTINEL_CONFIG_PART_NAME = "sentinelFile";

    /** multipart中任务JSON部分使用的文件名。 */
    public static final String TASK_PART_FILE_NAME = "task.json";

    /** 任务下发超时时间，单位为毫秒。 */
    public static final int TIMEOUT_MS = 10000;

    /** Redis源码包上传及等待执行器接收确认的超时时间。 */
    public static final int VERSION_UPLOAD_TIMEOUT_MS = 120000;

    /** 配置文件上传及等待执行器接收确认的超时时间。 */
    public static final int CONFIG_UPLOAD_TIMEOUT_MS = 30000;

    /** 工具类不允许实例化。 */
    private AgentTaskConstants() {
    }
}
