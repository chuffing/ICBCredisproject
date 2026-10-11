package com.redisops.platform.serverswitch.model;

import lombok.Data;

/**
 * 服务器切换请求中的一台新服务器。
 */
@Data
public class ServerSwitchNodeRequest {

    private String hostIp;
}
