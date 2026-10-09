package com.redisops.platform.agent.status.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.redisops.platform.agent.status.entity.ExecutorRuntimeStatus;
import org.apache.ibatis.annotations.Mapper;

/** 执行器最新运行状态的数据访问入口。 */
@Mapper
public interface ExecutorRuntimeStatusMapper extends BaseMapper<ExecutorRuntimeStatus> {
}