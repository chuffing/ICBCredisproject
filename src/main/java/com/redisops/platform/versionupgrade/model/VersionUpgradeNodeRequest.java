package com.redisops.platform.versionupgrade.model;

import lombok.Data;

/**
 * 版本升级请求中的一台目标服务器。
 */
@Data
public class VersionUpgradeNodeRequest {

    private String executorId;
}
