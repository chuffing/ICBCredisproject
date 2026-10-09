package com.redisops.platform.versionupgrade.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 平台保存和校验 Redis 版本包时使用的固定配置。
 */
@Component
@ConfigurationProperties(prefix = "platform.version-package")
public class VersionPackageProperties {

    private String storageDir = "./data/version-packages";
    private long maxBytes = 500L * 1024 * 1024;

    /** 获取平台版本包存储目录。 */
    public String getStorageDir() { return storageDir; }

    /** 设置平台版本包存储目录。 */
    public void setStorageDir(String storageDir) { this.storageDir = storageDir; }

    /** 获取单个版本包允许的最大字节数。 */
    public long getMaxBytes() { return maxBytes; }

    /** 设置单个版本包允许的最大字节数。 */
    public void setMaxBytes(long maxBytes) { this.maxBytes = maxBytes; }
}
