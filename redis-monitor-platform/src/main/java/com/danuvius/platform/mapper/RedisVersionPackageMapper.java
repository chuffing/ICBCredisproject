package com.danuvius.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.danuvius.platform.model.entity.RedisVersionPackage;
import org.apache.ibatis.annotations.Mapper;

/**
 * Redis版本安装包数据访问接口。
 */
@Mapper
public interface RedisVersionPackageMapper
        extends BaseMapper<RedisVersionPackage> {
}
