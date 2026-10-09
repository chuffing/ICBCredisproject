package com.redisops.platform.operation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.redisops.platform.operation.entity.OperationTaskStep;
import org.apache.ibatis.annotations.Mapper;

/** operation_task_step 表的数据访问入口。 */
@Mapper
public interface OperationTaskStepMapper extends BaseMapper<OperationTaskStep> {
}
