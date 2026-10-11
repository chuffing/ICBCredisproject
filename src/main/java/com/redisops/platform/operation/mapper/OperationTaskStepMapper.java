package com.redisops.platform.operation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.redisops.platform.operation.entity.OperationTaskStep;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** operation_task_step 表的数据访问入口。 */
@Mapper
public interface OperationTaskStepMapper extends BaseMapper<OperationTaskStep> {

    /** 批量插入运维任务步骤，减少数据库交互次数。 */
    @Insert({
            "<script>",
            "INSERT INTO operation_task_step",
            "(task_no, step_no, executor_id, operation_type, step_status, request_params, dispatch_count)",
            "VALUES",
            "<foreach collection='steps' item='step' separator=','>",
            "(#{step.taskNo}, #{step.stepNo}, #{step.executorId},",
            " #{step.operationType}, #{step.stepStatus}, #{step.requestParams}, #{step.dispatchCount})",
            "</foreach>",
            "</script>"
    })
    int insertBatch(@Param("steps") List<OperationTaskStep> steps);

}
