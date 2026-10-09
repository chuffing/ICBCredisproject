package com.redisops.platform.operation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.redisops.platform.operation.entity.OperationTask;
import org.apache.ibatis.annotations.Mapper;

/** operation_task 表的数据访问入口。 */
@Mapper
public interface OperationTaskMapper extends BaseMapper<OperationTask> {
}
