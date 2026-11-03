package com.danuvius.platform.common.constant;

/**
 * 平台与执行器之间的运维任务参数名称。
 */
public final class AgentTaskParamConstants {

    /** Redis版本安装包记录编号。 */
    public static final String PACKAGE_ID = "packageId";

    /** 目标Redis版本。 */
    public static final String TARGET_VERSION = "targetVersion";

    /** Redis版本安装包SHA256摘要。 */
    public static final String PACKAGE_SHA256 = "packageSha256";

    /** Redis配置文件SHA256摘要。 */
    public static final String REDIS_CONFIG_SHA256 =
            "redisConfigSha256";

    /** Sentinel配置文件SHA256摘要。 */
    public static final String SENTINEL_CONFIG_SHA256 =
            "sentinelConfigSha256";

    private AgentTaskParamConstants() {
    }
}
