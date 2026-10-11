package com.redisops.platform.versionupgrade.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 平台保存和校验 Redis 版本包时使用的固定配置。
 */
@Component
@ConfigurationProperties(prefix = "platform.version-package")
@Data
public class VersionPackageProperties {

    private String storageDir = "./data/version-packages";
    private long maxBytes = 500L * 1024 * 1024;
}
