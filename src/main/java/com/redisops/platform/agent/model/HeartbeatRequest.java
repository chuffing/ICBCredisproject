package com.redisops.platform.agent.model;

import lombok.Data;
import lombok.ToString;
import java.util.Map;

/**
 * 执行器每 30 秒发送的心跳请求。
 */
@Data
public class HeartbeatRequest {

    @ToString.Exclude
    private String token;
    private String agentId;
    private String status;
    private String hostname;
    private String ip;
    private String cpuUsage;
    private String memUsage;
    private Long lastCollectTime;
    private Map<String, String> extra;
}
