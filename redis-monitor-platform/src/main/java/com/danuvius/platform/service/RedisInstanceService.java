package com.danuvius.platform.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.danuvius.platform.model.entity.RedisInstance;

import java.time.LocalDateTime;

/**
 * Redis 实例维护
 */
public interface RedisInstanceService extends IService<RedisInstance> {

    /**
     * 更新实例表，心跳、运行状态
     * @param instanceId
     * @param now
     * @param runStatus
     * @return
     */
    Boolean updateHeartbeatAndStatus(String instanceId, LocalDateTime now, String runStatus);
}
