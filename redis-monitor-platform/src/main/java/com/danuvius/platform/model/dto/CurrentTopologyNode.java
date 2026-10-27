package com.danuvius.platform.model.dto;

import com.danuvius.platform.model.entity.ExecutorInfo;
import com.danuvius.platform.model.entity.RedisInstance;
import com.danuvius.platform.model.entity.SentinelNodeInfo;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 当前正式纳管拓扑中的单台运维节点。
 */
@Getter
@RequiredArgsConstructor
public class CurrentTopologyNode {

    /** 当前服务器上的Redis实例。 */
    private final RedisInstance redisInstance;

    /** 当前服务器上的Sentinel节点。 */
    private final SentinelNodeInfo sentinelNode;

    /** 与Redis实例绑定的执行器。 */
    private final ExecutorInfo executorInfo;
}
