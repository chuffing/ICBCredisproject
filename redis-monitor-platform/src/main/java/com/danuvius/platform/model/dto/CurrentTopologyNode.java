package com.danuvius.platform.model.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 当前正式纳管拓扑中的单台运维节点。
 */
@Getter
@RequiredArgsConstructor
public class CurrentTopologyNode {

    /** 当前服务器绑定的执行器编号。 */
    private final String executorId;

    /** 当前服务器Redis备份配置的SHA256摘要。 */
    private final String redisConfigSha256;

    /** 当前服务器Sentinel备份配置的SHA256摘要。 */
    private final String sentinelConfigSha256;
}
