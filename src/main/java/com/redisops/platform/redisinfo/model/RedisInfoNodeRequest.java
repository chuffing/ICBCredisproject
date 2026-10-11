package com.redisops.platform.redisinfo.model;

import lombok.Data;

/**
 * 初始化正式Redis拓扑时的一台服务器。
 */
@Data
public class RedisInfoNodeRequest {

    private String hostIp;
    private String role;
}
