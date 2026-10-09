package com.redisops.platform.redisinfo.model;

import java.util.List;

/**
 * 首次把公司已有Redis拓扑登记为平台正式纳管环境的请求。
 */
public class InitializeRedisInfoRequest {

    private String redisPassword;
    private String redisVersion;
    private Integer redisPort;
    private Integer sentinelPort;
    private List<RedisInfoNodeRequest> nodes;

    /** 获取Redis明文密码；该值只在请求处理期间存在。 */ public String getRedisPassword() { return redisPassword; }
    /** 设置Redis明文密码。 */ public void setRedisPassword(String value) { redisPassword = value; }
    /** 获取当前Redis版本。 */ public String getRedisVersion() { return redisVersion; }
    /** 设置当前Redis版本。 */ public void setRedisVersion(String value) { redisVersion = value; }
    /** 获取Redis端口。 */ public Integer getRedisPort() { return redisPort; }
    /** 设置Redis端口。 */ public void setRedisPort(Integer value) { redisPort = value; }
    /** 获取Sentinel端口。 */ public Integer getSentinelPort() { return sentinelPort; }
    /** 设置Sentinel端口。 */ public void setSentinelPort(Integer value) { sentinelPort = value; }
    /** 获取三台正式节点。 */ public List<RedisInfoNodeRequest> getNodes() { return nodes; }
    /** 设置三台正式节点。 */ public void setNodes(List<RedisInfoNodeRequest> value) { nodes = value; }
}
