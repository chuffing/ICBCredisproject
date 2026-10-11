package com.redisops.platform.agent.config;

import lombok.Data;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 平台与执行器通信使用的配置。
 */
@Configuration
@ConfigurationProperties(prefix = "platform.agent")
@Data
public class AgentPlatformProperties {

    @ToString.Exclude
    private String registerCode;
    @ToString.Exclude
    private String tokenSecret;
    @ToString.Exclude
    private String configSecret;
    private long offlineTimeoutSeconds = 60L;
}
