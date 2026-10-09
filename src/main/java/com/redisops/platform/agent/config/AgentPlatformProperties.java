package com.redisops.platform.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 平台与执行器通信使用的配置。
 */
@Configuration
@ConfigurationProperties(prefix = "platform.agent")
public class AgentPlatformProperties {

    private String registerCode;
    private String tokenSecret;
    private String configSecret;
    private long offlineTimeoutSeconds = 60L;

    /** 获取执行器注册码。 */
    public String getRegisterCode() { return registerCode; }

    /** 设置执行器注册码。 */
    public void setRegisterCode(String registerCode) { this.registerCode = registerCode; }

    /** 获取令牌加密密钥。 */
    public String getTokenSecret() { return tokenSecret; }

    /** 设置令牌加密密钥。 */
    public void setTokenSecret(String tokenSecret) { this.tokenSecret = tokenSecret; }

    /** 获取配置文件加密密钥。 */
    public String getConfigSecret() { return configSecret; }

    /** 设置配置文件加密密钥。 */
    public void setConfigSecret(String configSecret) { this.configSecret = configSecret; }

    /** 获取离线判定秒数。 */
    public long getOfflineTimeoutSeconds() { return offlineTimeoutSeconds; }

    /** 设置离线判定秒数。 */
    public void setOfflineTimeoutSeconds(long offlineTimeoutSeconds) {
        this.offlineTimeoutSeconds = offlineTimeoutSeconds;
    }
}
