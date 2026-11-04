package com.danuvius.platform.task;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.danuvius.platform.common.constant.CommonConstant;
import com.danuvius.platform.mapper.ExecutorInfoMapper;
import com.danuvius.platform.mapper.RedisInstanceMapper;
import com.danuvius.platform.model.entity.ExecutorInfo;
import com.danuvius.platform.model.entity.RedisInstance;
import com.danuvius.platform.model.enums.instance.ManagementStatusEnum;
import com.danuvius.platform.model.enums.instance.RunStatusEnum;
import com.danuvius.platform.util.CacheKeyUtil;
import com.github.benmanes.caffeine.cache.Cache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 执行器 / Redis 实例在线状态定时校正
 * 执行器按固定间隔主动扫描，超过离线阈值未心跳的记录置为离线
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExecutorStatusCheckTask {

    private final ExecutorInfoMapper executorInfoMapper;
    private final RedisInstanceMapper redisInstanceMapper;
    private final Cache<String, Object> caffeineCache;

    /** 运行状态离线阈值：连续未收到心跳/上报超过该秒数判为离线 */
    @Value("${platform.offline-threshold:70}")
    private long offlineThreshold;

    @Scheduled(fixedDelayString = "${platform.status-check.fixed-delay-ms:30000}")
    public void correctOfflineStatus() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime before = now.minusSeconds(offlineThreshold);

        // 执行器心跳超时：只更新当前仍为在线或状态为空的记录，避免重复 UPDATE 在线记录
        int executorRows = executorInfoMapper.update(null, new LambdaUpdateWrapper<ExecutorInfo>()
                .set(ExecutorInfo::getExecutorStatus, CommonConstant.OFFLINE)
                .set(ExecutorInfo::getUpdatedTime, now)
                .and(w -> w.eq(ExecutorInfo::getExecutorStatus, CommonConstant.ONLINE)
                        .or()
                        .isNull(ExecutorInfo::getExecutorStatus))
                .and(w -> w.lt(ExecutorInfo::getLastHeartbeatTime, before)
                        .or()
                        .isNull(ExecutorInfo::getLastHeartbeatTime))
        );

        // Redis 实例心跳/上报超时：执行器断联后，其绑定实例也会随之超时
        int instanceRows = redisInstanceMapper.update(null, new LambdaUpdateWrapper<RedisInstance>()
                .set(RedisInstance::getRunStatus, RunStatusEnum.RUN_OFFLINE.getCode())
                .set(RedisInstance::getUpdatedTime, now)
                .and(w -> w.ne(RedisInstance::getRunStatus, RunStatusEnum.RUN_OFFLINE.getCode())
                        .or()
                        .isNull(RedisInstance::getRunStatus))
                .and(w -> w.lt(RedisInstance::getLastHeartbeatTime, before)
                        .or()
                        .isNull(RedisInstance::getLastHeartbeatTime))
        );

        if (executorRows > 0 || instanceRows > 0) {
            // 数据库状态已变化，失效相关读缓存，避免前端继续展示在线
            caffeineCache.invalidate(CacheKeyUtil.instanceList(ManagementStatusEnum.ACTIVE.getCode()));
            caffeineCache.invalidate(CacheKeyUtil.instanceList(ManagementStatusEnum.INACTIVE.getCode()));
            caffeineCache.invalidate(CacheKeyUtil.TOPOLOGY);
            caffeineCache.invalidate(CacheKeyUtil.NODES);
            log.info("[状态校正] 执行器离线 {} 个，Redis实例离线 {} 个", executorRows, instanceRows);
        } else {
            log.debug("[状态校正] 无超时执行器/实例");
        }
    }
}
