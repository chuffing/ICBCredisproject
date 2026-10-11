package com.redisops.platform.agent.configsnapshot.model;

import lombok.Getter;

/**
 * 平台解密并重新校验后的两份配置文件。
 *
 * <p>该对象只在平台内部向 Agent 下发配置时使用，不作为控制器响应返回，
 * 避免 Redis 密码等配置正文通过普通查询接口泄露。</p>
 */
@Getter
public class ConfigSnapshotFiles {

    private final byte[] redisConfig;
    private final String redisConfigSha256;
    private final byte[] sentinelConfig;
    private final String sentinelConfigSha256;

    /** 保存已经通过摘要校验的配置正文和摘要。 */
    public ConfigSnapshotFiles(byte[] redisConfig,
                               String redisConfigSha256,
                               byte[] sentinelConfig,
                               String sentinelConfigSha256) {
        this.redisConfig = redisConfig.clone();
        this.redisConfigSha256 = redisConfigSha256;
        this.sentinelConfig = sentinelConfig.clone();
        this.sentinelConfigSha256 = sentinelConfigSha256;
    }

    /** 获取 Redis 配置原始字节的副本。 */
    public byte[] getRedisConfig() { return redisConfig.clone(); }

    /** 获取 Sentinel 配置原始字节的副本。 */
    public byte[] getSentinelConfig() { return sentinelConfig.clone(); }
}
