package com.redisops.platform.agent.model;

import lombok.Data;
import lombok.ToString;
import java.util.Map;

/**
 * 执行器首次注册请求。
 */
@Data
public class RegisterRequest {

    @ToString.Exclude
    private String registerCode;
    private String agentName;
    private String hostname;
    private String ip;
    private Integer agentPort;
    private String os;
    private Map<String, String> redisInfo;
}
