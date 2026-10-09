package com.redisops.platform.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.redisops.platform.agent.entity.ExecutorInfo;
import org.apache.ibatis.annotations.Mapper;

/**
 * executor_info 表的数据访问入口。
 */
@Mapper
public interface ExecutorInfoMapper extends BaseMapper<ExecutorInfo> {
}
