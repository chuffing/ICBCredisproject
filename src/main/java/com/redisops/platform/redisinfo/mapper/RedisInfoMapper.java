package com.redisops.platform.redisinfo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.redisops.platform.redisinfo.entity.RedisInfo;
import org.apache.ibatis.annotations.Mapper;

/**
 * redis_info表的MyBatis-Plus访问入口。
 */
@Mapper
public interface RedisInfoMapper extends BaseMapper<RedisInfo> {
}
