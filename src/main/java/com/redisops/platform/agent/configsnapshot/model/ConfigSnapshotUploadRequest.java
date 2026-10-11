package com.redisops.platform.agent.configsnapshot.model;

import lombok.Data;
import lombok.ToString;

/**
 * 执行器上传本机备份配置的请求。
 */
@Data
public class ConfigSnapshotUploadRequest {

    private String agentId;
    @ToString.Exclude
    private String redisConfigBase64;
    @ToString.Exclude
    private String sentinelConfigBase64;
}
