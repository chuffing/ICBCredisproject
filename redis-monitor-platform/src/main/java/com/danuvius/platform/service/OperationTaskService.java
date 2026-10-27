package com.danuvius.platform.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.danuvius.platform.model.dto.TaskResultReportDTO;
import com.danuvius.platform.model.entity.OperationTask;
import com.danuvius.platform.model.entity.OperationTaskStep;
import com.danuvius.platform.model.enums.OperationTaskTypeEnum;

import java.util.List;
import java.util.Map;

/**
 * 运维任务服务。
 */
public interface OperationTaskService extends IService<OperationTask> {

    /**
     * 查询当前尚未结束的高风险运维任务。
     *
     * @return 当前活动任务，不存在时返回null
     */
    OperationTask getActiveTask();

    /**
     * 根据任务编号查询运维任务。
     *
     * @param taskNo 运维任务编号
     * @return 运维任务，不存在时返回null
     */
    OperationTask getTaskByTaskNo(String taskNo);

    /**
     * 检查其他高风险运维任务后创建新任务。
     *
     * @param taskType 任务类型
     * @param operationReason 操作原因
     * @param createdBy 操作人
     * @param requestParams 任务参数快照JSON
     * @return 已创建的运维任务
     */
    OperationTask createTask(
            OperationTaskTypeEnum taskType,
            String operationReason,
            String createdBy,
            String requestParams);

    /**
     * 批量保存运维任务步骤。
     *
     * @param steps 运维任务步骤
     */
    void saveSteps(List<OperationTaskStep> steps);

    /**
     * 根据任务步骤状态计算当前执行阶段。
     *
     * @param taskNo 运维任务编号
     * @return 当前阶段编号；任务没有步骤时返回null
     */
    Integer getCurrentStepNo(String taskNo);

    /**
     * 查询当前任务阶段的步骤。
     */
    List<OperationTaskStep> getStageSteps(
            String taskNo,
            Integer stepNo);

    /**
     * 启动当前阶段，将全部步骤改为下发中。
     */
    List<OperationTaskStep> startStageDispatch(
            String taskNo,
            Integer stepNo,
            List<OperationTaskStep> stageSteps);

    /**
     * 阶段下发前检查失败，将整个阶段和总任务收口为失败。
     */
    void rejectStageBeforeDispatch(
            String taskNo,
            Integer stepNo,
            List<OperationTaskStep> stageSteps,
            Map<String, String> rejectedReasons);

    /** 保存执行器已接收任务。 */
    void markDispatched(
            String taskNo,
            Integer stepNo,
            String executorId);

    /** 保存执行器明确拒绝任务。 */
    void markRejected(
            String taskNo,
            Integer stepNo,
            String executorId,
            String reason);

    /** 保存HTTP下发结果未知。 */
    void markDispatchResultUnknown(
            String taskNo,
            Integer stepNo,
            String executorId,
            String reason);

    /** 检查下发结果是否已经足以结束总任务。 */
    void finishDispatchStage(String taskNo, Integer stepNo);

    /** 独立保存执行器上报的步骤结果。 */
    void saveStepResult(TaskResultReportDTO request);

    /** 将长时间未上报的步骤标记为结果未知。 */
    boolean markStepResultUnknown(
            String taskNo,
            Integer stepNo,
            String executorId,
            String reason);

    /**
     * 汇总当前阶段。
     *
     * @return 下一阶段编号；无需继续下发时返回null
     */
    Integer finishStage(String taskNo, Integer stepNo);

    /** 阶段汇总异常时，将总任务标记为结果未知。 */
    void markTaskResultUnknown(String taskNo, String reason);

    /**
     * 人工结束结果未知的运维任务。
     *
     * @param taskNo 任务编号
     * @param terminationReason 人工结束原因
     * @param terminatedBy 人工结束操作人
     */
    void manuallyTerminateTask(
            String taskNo,
            String terminationReason,
            String terminatedBy);
}
