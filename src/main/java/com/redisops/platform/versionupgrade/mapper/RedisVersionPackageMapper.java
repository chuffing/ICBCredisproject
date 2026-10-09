package com.redisops.platform.versionupgrade.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.redisops.platform.versionupgrade.entity.RedisVersionPackage;
import org.apache.ibatis.annotations.Mapper;

/** redis_version_package 表的数据访问入口。 */
@Mapper
public interface RedisVersionPackageMapper extends BaseMapper<RedisVersionPackage> {
}
