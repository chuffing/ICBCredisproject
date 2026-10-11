package com.redisops.platform.redisinfo.model;

import lombok.Data;
import lombok.ToString;
import java.util.List;

/**
 * 首次把公司已有Redis拓扑登记为平台正式纳管环境的请求。
 */
@Data
public class InitializeRedisInfoRequest {

    @ToString.Exclude
    private String redisPassword;
    private String redisVersion;
    private Integer redisPort;
    private Integer sentinelPort;
    private List<RedisInfoNodeRequest> nodes;
}
