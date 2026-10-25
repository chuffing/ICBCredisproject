package com.danuvius.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.danuvius.platform.model.entity.OperationTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 运维任务Mapper。
 */
@Mapper
public interface OperationTaskMapper extends BaseMapper<OperationTask> {

    /**
     * 查询当前尚未结束的高风险运维任务。
     *
     * @return 当前活动任务，不存在时返回null
     */
    OperationTask selectActiveTask();

    /**
     * 根据任务编号查询运维任务。
     *
     * @param taskNo 运维任务编号
     * @return 运维任务，不存在时返回null
     */
    OperationTask selectTaskByTaskNo(@Param("taskNo") String taskNo);

    /**
     * 新增运维任务。
     *
     * @param task 运维任务
     * @return 影响行数
     */
    int insertOperationTask(@Param("task") OperationTask task);

    /**
     * 根据任务编号查询并锁定运维任务。
     *
     * @param taskNo 任务编号
     * @return 运维任务，不存在时返回null
     */
    OperationTask selectTaskForUpdate(@Param("taskNo") String taskNo);

    /**
     * 更新运维任务最终结果。
     *
     * @param task 运维任务
     * @return 影响行数
     */
    int updateTaskResult(@Param("task") OperationTask task);

    /**
     * 保存运维任务的阶段下发状态。
     *
     * @param task 运维任务
     * @return 影响行数
     */
    int updateTaskExecutionState(@Param("task") OperationTask task);

    /**
     * 保存任务人工结束状态和审计信息。
     *
     * @param task 运维任务
     * @return 影响行数
     */
    int updateTaskManualTermination(@Param("task") OperationTask task);
}
