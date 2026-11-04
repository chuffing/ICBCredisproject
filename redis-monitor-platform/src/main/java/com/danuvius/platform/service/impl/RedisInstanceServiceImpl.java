package com.danuvius.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.danuvius.platform.common.constant.CommonConstant;
import com.danuvius.platform.mapper.RedisInstanceMapper;
import com.danuvius.platform.model.entity.RedisInstance;
import com.danuvius.platform.service.RedisInstanceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Redis实例 心跳/运行状态同步
 */
@Slf4j
@Service
public class RedisInstanceServiceImpl extends ServiceImpl<RedisInstanceMapper, RedisInstance>
        implements RedisInstanceService {
    /**
     * 更新实例表，心跳、运行状态
     * @param instanceId
     * @param now
     * @param runStatus
     * @return
     */
    @Override
    public Boolean updateHeartbeatAndStatus(String instanceId, LocalDateTime now, String runStatus) {
        int rows=baseMapper.update(null,new LambdaUpdateWrapper<RedisInstance>()
                .set(RedisInstance::getLastHeartbeatTime,now)
                .set(RedisInstance::getRunStatus,runStatus)
                .set(RedisInstance::getUpdatedTime,now)
                .eq(RedisInstance::getInstanceId,instanceId)
        );
        if(rows>0) {
            log.info("执行器上报实例心跳、运行状态更新:{}",CommonConstant.SUCCESS);
            return true;
        }
        else {
            log.info("执行器上报实例心跳、运行状态更新:{}",CommonConstant.FAILURE);
            return false;
        }
    }
}
