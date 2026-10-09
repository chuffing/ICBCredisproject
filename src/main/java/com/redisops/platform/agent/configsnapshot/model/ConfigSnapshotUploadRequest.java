package com.redisops.platform.agent.configsnapshot.model;

/**
 * 执行器上传本机备份配置的请求。
 */
public class ConfigSnapshotUploadRequest {

    private String agentId;
    private String redisConfigBase64;
    private String sentinelConfigBase64;

    /** 获取执行器编号。 */ public String getAgentId() { return agentId; }
    /** 设置执行器编号。 */ public void setAgentId(String value) { this.agentId = value; }
    /** 获取Redis备份配置的Base64正文。 */ public String getRedisConfigBase64() { return redisConfigBase64; }
    /** 设置Redis备份配置的Base64正文。 */ public void setRedisConfigBase64(String value) { this.redisConfigBase64 = value; }
    /** 获取Sentinel备份配置的Base64正文。 */ public String getSentinelConfigBase64() { return sentinelConfigBase64; }
    /** 设置Sentinel备份配置的Base64正文。 */ public void setSentinelConfigBase64(String value) { this.sentinelConfigBase64 = value; }
}
