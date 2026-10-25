package com.danuvius.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.danuvius.platform.model.entity.OperationTaskStep;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 运维任务步骤Mapper。
 */
@Mapper
public interface OperationTaskStepMapper
        extends BaseMapper<OperationTaskStep> {

    /**
     * 批量新增运维任务步骤。
     *
     * @param steps 运维任务步骤
     * @return 影响行数
     */
    int insertBatch(@Param("steps") List<OperationTaskStep> steps);

    /**
     * 查询并锁定执行器上报结果对应的任务步骤。
     *
     * @param taskNo 任务编号
     * @param stepNo 步骤编号
     * @param executorId 执行器编号
     * @return 运维任务步骤，不存在时返回null
     */
    OperationTaskStep selectTaskStepForUpdate(
            @Param("taskNo") String taskNo,
            @Param("stepNo") Integer stepNo,
            @Param("executorId") String executorId);

    /**
     * 保存执行器上报的任务步骤结果。
     *
     * @param step 运维任务步骤结果
     * @return 影响行数
     */
    int updateTaskStepResult(@Param("step") OperationTaskStep step);

    /**
     * 将单个步骤从已创建修改为下发中。
     */
    int markStepDispatching(
            @Param("taskNo") String taskNo,
            @Param("stepNo") Integer stepNo,
            @Param("executorId") String executorId,
            @Param("currentStatus") String currentStatus,
            @Param("targetStatus") String targetStatus,
            @Param("dispatchTime") LocalDateTime dispatchTime);

    /**
     * 当前状态符合预期时更新单个步骤状态。
     */
    int updateStepStatusIfCurrent(
            @Param("taskNo") String taskNo,
            @Param("stepNo") Integer stepNo,
            @Param("executorId") String executorId,
            @Param("currentStatus") String currentStatus,
            @Param("targetStatus") String targetStatus,
            @Param("failureReason") String failureReason,
            @Param("updatedTime") LocalDateTime updatedTime);

    /**
     * 查询指定任务阶段的全部步骤。
     *
     * @param taskNo 任务编号
     * @param stepNo 步骤编号
     * @return 当前阶段的任务步骤
     */
    List<OperationTaskStep> selectTaskStageSteps(
            @Param("taskNo") String taskNo,
            @Param("stepNo") Integer stepNo);

    /**
     * 查询当前阶段之后的下一个阶段编号。
     *
     * @param taskNo 任务编号
     * @param stepNo 当前阶段编号
     * @return 下一阶段编号，不存在时返回null
     */
    Integer selectNextStepNo(
            @Param("taskNo") String taskNo,
            @Param("stepNo") Integer stepNo);

    /**
     * 查询活动任务中正在等待执行器上报结果的步骤。
     *
     * @param stepStatus 需要扫描的步骤状态
     * @param taskStatuses 允许扫描的总任务状态
     * @return 正在等待执行结果的任务步骤
     */
    List<OperationTaskStep> selectWaitingResultSteps(
            @Param("stepStatus") String stepStatus,
            @Param("taskStatuses") List<String> taskStatuses);
}
