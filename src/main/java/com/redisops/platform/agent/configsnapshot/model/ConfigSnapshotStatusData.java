package com.redisops.platform.agent.configsnapshot.model;

import lombok.Data;

/**
 * 平台返回给执行器的基准配置状态和可信摘要。
 */
@Data
public class ConfigSnapshotStatusData {

    private final boolean initialized;
    private final String redisConfigSha256;
    private final String sentinelConfigSha256;
}
