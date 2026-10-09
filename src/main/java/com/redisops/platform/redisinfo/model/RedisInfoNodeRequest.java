package com.redisops.platform.redisinfo.model;

/**
 * 初始化正式Redis拓扑时的一台服务器。
 */
public class RedisInfoNodeRequest {

    private String hostIp;
    private String role;

    /** 获取服务器IP。 */ public String getHostIp() { return hostIp; }
    /** 设置服务器IP。 */ public void setHostIp(String value) { hostIp = value; }
    /** 获取节点角色。 */ public String getRole() { return role; }
    /** 设置节点角色。 */ public void setRole(String value) { role = value; }
}
