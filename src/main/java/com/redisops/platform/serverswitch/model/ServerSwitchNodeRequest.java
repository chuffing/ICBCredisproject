package com.redisops.platform.serverswitch.model;

/**
 * 服务器切换请求中的一台新服务器。
 */
public class ServerSwitchNodeRequest {

    private String hostIp;

    /** 获取用户输入的新服务器IP。 */
    public String getHostIp() { return hostIp; }

    /** 设置用户输入的新服务器IP。 */
    public void setHostIp(String value) { hostIp = value; }
}
