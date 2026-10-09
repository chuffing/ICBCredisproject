package com.redisops.platform.agent.configsnapshot.model;

/**
 * 平台返回给执行器的基准配置状态和可信摘要。
 */
public class ConfigSnapshotStatusData {

    private final boolean initialized;
    private final String redisConfigSha256;
    private final String sentinelConfigSha256;

    /** 创建配置快照状态。 */
    public ConfigSnapshotStatusData(boolean initialized,
                                    String redisConfigSha256,
                                    String sentinelConfigSha256) {
        this.initialized = initialized;
        this.redisConfigSha256 = redisConfigSha256;
        this.sentinelConfigSha256 = sentinelConfigSha256;
    }

    /** 判断平台是否已有基准配置。 */ public boolean isInitialized() { return initialized; }
    /** 获取平台保存的Redis配置摘要。 */ public String getRedisConfigSha256() { return redisConfigSha256; }
    /** 获取平台保存的Sentinel配置摘要。 */ public String getSentinelConfigSha256() { return sentinelConfigSha256; }
}
