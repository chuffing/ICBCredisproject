package com.danuvius.platform.service.impl;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.danuvius.platform.common.constant.CommonConstant;
import com.danuvius.platform.common.constant.OperationTaskConstants;
import com.danuvius.platform.common.exception.BusinessCode;
import com.danuvius.platform.common.exception.BusinessException;
import com.danuvius.platform.mapper.OperationTaskMapper;
import com.danuvius.platform.mapper.OperationTaskStepMapper;
import com.danuvius.platform.mapper.RedisVersionPackageMapper;
import com.danuvius.platform.model.dto.OperationResultDTO;
import com.danuvius.platform.model.dto.TaskResultReportDTO;
import com.danuvius.platform.model.dto.VersionUpgradeRequestDTO;
import com.danuvius.platform.model.entity.OperationTask;
import com.danuvius.platform.model.entity.OperationTaskStep;
import com.danuvius.platform.model.entity.RedisInstance;
import com.danuvius.platform.model.entity.RedisVersionPackage;
import com.danuvius.platform.model.enums.OperationTaskStatusEnum;
import com.danuvius.platform.model.enums.OperationTaskStepStatusEnum;
import com.danuvius.platform.model.enums.OperationTaskTypeEnum;
import com.danuvius.platform.model.enums.StatusEnum;
import com.danuvius.platform.service.AuditLogService;
import com.danuvius.platform.service.OperationTaskService;
import com.danuvius.platform.service.RedisInstanceService;
import com.danuvius.platform.service.TaskNoService;
import com.danuvius.platform.util.CacheKeyUtil;
import com.github.benmanes.caffeine.cache.Cache;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

/**
 * 运维任务服务实现。
 *
 * <p>所有运维任务和任务步骤的数据库状态变更都集中在这里，
 * 下发服务和结果服务只负责业务执行顺序。</p>
 */
@Service
@RequiredArgsConstructor
public class OperationTaskServiceImpl
        extends ServiceImpl<OperationTaskMapper, OperationTask>
        implements OperationTaskService {

    private static final ZoneId PLATFORM_ZONE_ID =
            ZoneId.of("Asia/Shanghai");

    private final OperationTaskMapper operationTaskMapper;

    private final OperationTaskStepMapper operationTaskStepMapper;

    private final AuditLogService auditLogService;

    private final TaskNoService taskNoService;

    private final ServerSwitchCompletionService
            serverSwitchCompletionService;

    private final RedisInstanceService redisInstanceService;

    private final RedisVersionPackageMapper versionPackageMapper;

    private final Cache<String, Object> caffeineCache;

    @Override
    public OperationTask getActiveTask() {
        return operationTaskMapper.selectActiveTask();
    }

    @Override
    public OperationTask getTaskByTaskNo(String taskNo) {
        return operationTaskMapper.selectTaskByTaskNo(taskNo);
    }

    /**
     * 检查其他高风险运维任务后创建新任务。
     */
    @Override
    @Transactional
    public OperationTask createTask(
            OperationTaskTypeEnum taskType,
            String operationReason,
            String createdBy,
            String requestParams) {

        // ======== 1. 检查是否存在未结束任务（CREATED、RUNNING、RESULT_UNKNOWN） ========

        OperationTask activeTask = getActiveTask();
        if (activeTask != null) {
            throw new BusinessException(
                    BusinessCode.OPERATION_ERROR.getCode(),
                    "当前存在尚未结束的高风险运维任务："
                            + activeTask.getTaskNo()
                            + "（" + activeTask.getTaskType() + "）"
            );
        }

        // ======== 2. 组装并保存总任务 ========

        LocalDateTime now = LocalDateTime.now();
        OperationTask task = new OperationTask();
        task.setTaskNo(taskNoService.nextTaskNo());
        task.setTaskType(taskType.name());
        task.setTaskStatus(OperationTaskStatusEnum.CREATED.name());
        task.setOperationReason(operationReason.trim());
        task.setCreatedBy(createdBy.trim());
        task.setRequestParams(requestParams);
        task.setFailureReason("");
        task.setTerminationReason("");
        task.setTerminatedBy("");
        task.setCreatedTime(now);
        task.setUpdatedTime(now);

        int inserted = operationTaskMapper.insertOperationTask(task);
        if (inserted != 1) {
            throw new BusinessException(
                    BusinessCode.OPERATION_ERROR.getCode(),
                    "运维任务创建失败"
            );
        }

        return task;
    }

    /**
     * 批量保存运维任务步骤。
     */
    @Override
    public void saveSteps(List<OperationTaskStep> steps) {

        int inserted = operationTaskStepMapper.insertBatch(steps);
        if (inserted != steps.size()) {
            throw new BusinessException(
                    BusinessCode.OPERATION_ERROR.getCode(),
                    "运维任务步骤创建失败"
            );
        }
    }

    /**
     * 根据数据库中实际保存的步骤状态计算当前执行阶段。
     */
    @Override
    public Integer getCurrentStepNo(String taskNo) {

        // 1. 按照阶段编号查询本次任务的全部步骤。
        List<OperationTaskStep> taskSteps = operationTaskStepMapper.selectList(
                new LambdaQueryWrapper<OperationTaskStep>()
                        .eq(OperationTaskStep::getTaskNo, taskNo)
                        .orderByAsc(OperationTaskStep::getStepNo)
                        .orderByAsc(OperationTaskStep::getId)
        );
        if (taskSteps.isEmpty()) {
            return null;
        }

        // 2. 第一个没有全部成功的阶段，就是当前执行阶段。
        Integer currentStepNo = taskSteps.get(0).getStepNo();
        boolean currentStageSuccess = true;

        for (OperationTaskStep step : taskSteps) {
            if (!currentStepNo.equals(step.getStepNo())) {
                if (!currentStageSuccess) {
                    return currentStepNo;
                }

                currentStepNo = step.getStepNo();
                currentStageSuccess = true;
            }

            if (!OperationTaskStepStatusEnum.SUCCESS.name()
                    .equals(step.getStepStatus())) {
                currentStageSuccess = false;
            }
        }

        // 3. 全部阶段成功时返回最后一个阶段编号。
        return currentStepNo;
    }

    /**
     * 查询当前任务阶段的步骤。
     */
    @Override
    public List<OperationTaskStep> getStageSteps(
            String taskNo,
            Integer stepNo) {

        List<OperationTaskStep> stageSteps =
                operationTaskStepMapper.selectTaskStageSteps(
                        taskNo,
                        stepNo
                );
        if (stageSteps.isEmpty()) {
            throw new BusinessException(
                    BusinessCode.NOT_FOUND_ERROR.getCode(),
                    "未找到需要下发的运维任务阶段"
            );
        }

        return stageSteps;
    }

    /**
     * 启动当前阶段，将阶段内全部步骤改为下发中。
     */
    @Override
    @Transactional
    public List<OperationTaskStep> startStageDispatch(
            String taskNo,
            Integer stepNo,
            List<OperationTaskStep> stageSteps) {

        // ======== 1. 锁定并校验总任务 ========

        OperationTask task = operationTaskMapper.selectTaskForUpdate(taskNo);
        if (task == null) {
            throw new BusinessException(
                    BusinessCode.NOT_FOUND_ERROR.getCode(),
                    "未找到需要下发的运维任务"
            );
        }

        String taskStatus = task.getTaskStatus();
        if (!OperationTaskStatusEnum.CREATED.name().equals(taskStatus)
                && !OperationTaskStatusEnum.RUNNING.name()
                .equals(taskStatus)) {
            throw new BusinessException(
                    BusinessCode.OPERATION_ERROR.getCode(),
                    "当前运维任务状态不允许继续下发"
            );
        }

        // ======== 2. 首次下发时启动总任务 ========

        if (OperationTaskStatusEnum.CREATED.name().equals(taskStatus)) {
            saveTaskStatus(task, OperationTaskStatusEnum.RUNNING, null);
        }

        // ======== 3. 原子地将整个阶段改为下发中 ========

        LocalDateTime now = LocalDateTime.now();
        for (OperationTaskStep step : stageSteps) {
            int updated = operationTaskStepMapper.markStepDispatching(
                    taskNo,
                    stepNo,
                    step.getExecutorId(),
                    OperationTaskStepStatusEnum.CREATED.name(),
                    OperationTaskStepStatusEnum.DISPATCHING.name(),
                    now
            );
            if (updated != 1) {
                throw new BusinessException(
                        BusinessCode.OPERATION_ERROR.getCode(),
                        "任务步骤进入下发状态失败，执行器："
                                + step.getExecutorId()
                );
            }
            step.setStepStatus(
                    OperationTaskStepStatusEnum.DISPATCHING.name()
            );
            step.setDispatchTime(now);
        }

        return stageSteps;
    }

    /**
     * 阶段下发前检查失败，不发送HTTP，直接结束整个任务。
     */
    @Override
    @Transactional
    public void rejectStageBeforeDispatch(
            String taskNo,
            Integer stepNo,
            List<OperationTaskStep> stageSteps,
            Map<String, String> rejectedReasons) {

        // ======== 1. 锁定并校验总任务 ========

        OperationTask task = operationTaskMapper.selectTaskForUpdate(taskNo);
        if (task == null) {
            throw new BusinessException(
                    BusinessCode.NOT_FOUND_ERROR.getCode(),
                    "未找到需要结束的运维任务"
            );
        }

        String taskStatus = task.getTaskStatus();
        if (!OperationTaskStatusEnum.CREATED.name().equals(taskStatus)
                && !OperationTaskStatusEnum.RUNNING.name()
                .equals(taskStatus)) {
            return;
        }

        // ======== 2. 将当前阶段的全部步骤收口为失败 ========

        LocalDateTime now = LocalDateTime.now();
        for (OperationTaskStep step : stageSteps) {
            String reason = rejectedReasons.get(step.getExecutorId());
            if (reason == null) {
                reason = "同阶段执行器检查未通过，本步骤未下发";
            }

            int updated = operationTaskStepMapper.updateStepStatusIfCurrent(
                    taskNo,
                    stepNo,
                    step.getExecutorId(),
                    OperationTaskStepStatusEnum.CREATED.name(),
                    OperationTaskStepStatusEnum.FAILED.name(),
                    reason,
                    now
            );
            if (updated != 1) {
                throw new BusinessException(
                        BusinessCode.OPERATION_ERROR.getCode(),
                        "任务步骤拒绝状态保存失败，执行器："
                                + step.getExecutorId()
                );
            }
        }

        // ======== 3. 将总任务收口为失败 ========

        Map.Entry<String, String> firstRejected =
                rejectedReasons.entrySet().iterator().next();
        String failureReason = buildDispatchFailureReason(
                stepNo,
                firstRejected.getKey(),
                firstRejected.getValue()
        );
        saveTaskStatus(task, OperationTaskStatusEnum.FAILED, failureReason);
    }

    @Override
    @Transactional
    public void markDispatched(
            String taskNo,
            Integer stepNo,
            String executorId) {

        if (!taskAllowsStepUpdate(taskNo)) {
            return;
        }

        int updated = operationTaskStepMapper.updateStepStatusIfCurrent(
                taskNo,
                stepNo,
                executorId,
                OperationTaskStepStatusEnum.DISPATCHING.name(),
                OperationTaskStepStatusEnum.DISPATCHED.name(),
                "",
                LocalDateTime.now()
        );

        // 执行器可能已经快速回调，此时保留更靠后的终态结果。
        if (updated == 0) {
            validateStepAlreadyFinished(taskNo, stepNo, executorId);
        }
    }

    @Override
    @Transactional
    public void markRejected(
            String taskNo,
            Integer stepNo,
            String executorId,
            String reason) {

        if (!taskAllowsStepUpdate(taskNo)) {
            return;
        }

        int updated = operationTaskStepMapper.updateStepStatusIfCurrent(
                taskNo,
                stepNo,
                executorId,
                OperationTaskStepStatusEnum.DISPATCHING.name(),
                OperationTaskStepStatusEnum.FAILED.name(),
                reason,
                LocalDateTime.now()
        );
        if (updated != 1) {
            throw new BusinessException(
                    BusinessCode.OPERATION_ERROR.getCode(),
                    "任务步骤拒绝状态保存失败，执行器："
                            + executorId
            );
        }
    }

    @Override
    @Transactional
    public void markDispatchResultUnknown(
            String taskNo,
            Integer stepNo,
            String executorId,
            String reason) {

        if (!taskAllowsStepUpdate(taskNo)) {
            return;
        }

        int updated = operationTaskStepMapper.updateStepStatusIfCurrent(
                taskNo,
                stepNo,
                executorId,
                OperationTaskStepStatusEnum.DISPATCHING.name(),
                OperationTaskStepStatusEnum.RESULT_UNKNOWN.name(),
                reason,
                LocalDateTime.now()
        );

        // HTTP超时前执行器也可能已经回调，真实终态结果优先。
        if (updated == 0) {
            validateStepAlreadyFinished(taskNo, stepNo, executorId);
        }
    }

    /**
     * 所有HTTP下发结果保存后，检查是否需要结束总任务。
     */
    @Override
    @Transactional
    public void finishDispatchStage(String taskNo, Integer stepNo) {

        OperationTask task = operationTaskMapper.selectTaskForUpdate(taskNo);
        if (task == null) {
            throw new BusinessException(
                    BusinessCode.NOT_FOUND_ERROR.getCode(),
                    "未找到需要汇总下发结果的运维任务"
            );
        }
        if (!OperationTaskStatusEnum.RUNNING.name()
                .equals(task.getTaskStatus())) {
            return;
        }

        List<OperationTaskStep> stageSteps =
                operationTaskStepMapper.selectTaskStageSteps(
                        taskNo,
                        stepNo
                );
        if (stageSteps.isEmpty()) {
            throw new BusinessException(
                    BusinessCode.OPERATION_ERROR.getCode(),
                    "当前任务阶段不存在执行步骤"
            );
        }

        OperationTaskStep firstUnknownStep = null;
        OperationTaskStep firstFailedStep = null;

        for (OperationTaskStep step : stageSteps) {
            String status = step.getStepStatus();

            if (OperationTaskStepStatusEnum.RESULT_UNKNOWN.name()
                    .equals(status) && firstUnknownStep == null) {
                firstUnknownStep = step;
            } else if (OperationTaskStepStatusEnum.FAILED.name()
                    .equals(status) && firstFailedStep == null) {
                firstFailedStep = step;
            }

            // 只要有执行器已接收任务并正在执行，总任务就继续保持RUNNING。
            if (!isTerminalStepStatus(status)) {
                return;
            }
        }

        // 当前所有步骤都进入了终态。
        if (firstUnknownStep != null) {

            saveTaskStatus(
                    task,
                    OperationTaskStatusEnum.RESULT_UNKNOWN,
                    buildDispatchUnknownReason(firstUnknownStep)
            );
        } else if (firstFailedStep != null) {

            saveTaskStatus(
                    task,
                    OperationTaskStatusEnum.FAILED,
                    buildDispatchFailureReason(
                            stepNo,
                            firstFailedStep.getExecutorId(),
                            firstFailedStep.getFailureReason()
                    )
            );
        }
    }

    /**
     * 独立保存执行器上报的步骤结果。
     */
    @Override
    @Transactional
    public void saveStepResult(TaskResultReportDTO request) {

        // ======== 1. 锁定并校验总任务 ========

        OperationTask task = operationTaskMapper.selectTaskForUpdate(
                request.getTaskNo()
        );
        if (task == null) {
            throw new BusinessException(
                    BusinessCode.NOT_FOUND_ERROR.getCode(),
                    "未找到执行器上报结果对应的运维任务"
            );
        }
        if (!OperationTaskStatusEnum.RUNNING.name()
                .equals(task.getTaskStatus())) {
            throw new BusinessException(
                    BusinessCode.OPERATION_ERROR.getCode(),
                    "当前运维任务状态不允许上报执行结果"
            );
        }

        // ======== 2. 锁定并校验上报步骤 ========

        OperationTaskStep step =
                operationTaskStepMapper.selectTaskStepForUpdate(
                        request.getTaskNo(),
                        request.getStepNo(),
                        request.getAgentId()
                );
        if (step == null) {
            throw new BusinessException(
                    BusinessCode.NOT_FOUND_ERROR.getCode(),
                    "未找到执行器上报结果对应的任务步骤"
            );
        }
        if (!step.getOperationType().equals(request.getOperationType())) {
            throw new BusinessException(
                    BusinessCode.OPERATION_ERROR.getCode(),
                    "执行器上报的操作类型与任务步骤不一致"
            );
        }

        String stepStatus = step.getStepStatus();
        if (!OperationTaskStepStatusEnum.DISPATCHING.name()
                .equals(stepStatus)
                && !OperationTaskStepStatusEnum.DISPATCHED.name()
                .equals(stepStatus)) {
            throw new BusinessException(
                    BusinessCode.OPERATION_ERROR.getCode(),
                    "当前任务步骤状态不允许上报执行结果"
            );
        }

        // ======== 3. 保存当前步骤的客观执行结果 ========

        OperationResultDTO operationResult = parseOperationResult(
                request.getResult()
        );
        fillTaskStepResult(step, request, operationResult);

        int updated = operationTaskStepMapper.updateTaskStepResult(step);
        if (updated != 1) {
            throw new BusinessException(
                    BusinessCode.OPERATION_ERROR.getCode(),
                    "任务步骤执行结果保存失败"
            );
        }
    }

    @Override
    @Transactional
    public boolean markStepResultUnknown(
            String taskNo,
            Integer stepNo,
            String executorId,
            String reason) {

        OperationTask task = operationTaskMapper.selectTaskForUpdate(taskNo);
        if (task == null
                || !OperationTaskStatusEnum.RUNNING.name()
                .equals(task.getTaskStatus())) {
            return false;
        }

        // 只允许DISPATCHED进入RESULT_UNKNOWN，防止超时结果覆盖真实回调结果。
        int updated = operationTaskStepMapper.updateStepStatusIfCurrent(
                taskNo,
                stepNo,
                executorId,
                OperationTaskStepStatusEnum.DISPATCHED.name(),
                OperationTaskStepStatusEnum.RESULT_UNKNOWN.name(),
                reason,
                LocalDateTime.now()
        );

        return updated == 1;
    }

    /**
     * 独立汇总已经提交的阶段步骤结果。
     */
    @Override
    @Transactional
    public Integer finishStage(String taskNo, Integer stepNo) {

        // ======== 1. 锁定总任务 ========

        OperationTask task = operationTaskMapper.selectTaskForUpdate(taskNo);
        if (task == null) {
            throw new BusinessException(
                    BusinessCode.NOT_FOUND_ERROR.getCode(),
                    "未找到需要汇总阶段结果的运维任务"
            );
        }
        if (!OperationTaskStatusEnum.RUNNING.name()
                .equals(task.getTaskStatus())) {
            return null;
        }

        // ======== 2. 检查当前阶段是否全部结束 ========

        List<OperationTaskStep> stageSteps =
                operationTaskStepMapper.selectTaskStageSteps(
                        taskNo,
                        stepNo
                );
        if (stageSteps.isEmpty()) {
            throw new BusinessException(
                    BusinessCode.OPERATION_ERROR.getCode(),
                    "当前任务阶段不存在执行步骤"
            );
        }

        OperationTaskStep firstUnknownStep = null;
        OperationTaskStep firstFailedStep = null;

        for (OperationTaskStep step : stageSteps) {
            String status = step.getStepStatus();

            if (OperationTaskStepStatusEnum.RESULT_UNKNOWN.name()
                    .equals(status) && firstUnknownStep == null) {
                firstUnknownStep = step;
            } else if (OperationTaskStepStatusEnum.FAILED.name()
                    .equals(status) && firstFailedStep == null) {
                firstFailedStep = step;
            }

            // 当前阶段还有执行器没有上报，继续等待。
            if (!isTerminalStepStatus(status)) {
                return null;
            }
        }

        // ======== 3. 优先处理结果未知 ========

        if (firstUnknownStep != null) {
            saveTaskStatus(
                    task,
                    OperationTaskStatusEnum.RESULT_UNKNOWN,
                    buildResultUnknownReason(firstUnknownStep)
            );
            return null;
        }

        // ======== 4. 其次处理明确失败 ========

        if (firstFailedStep != null) {
            saveTaskStatus(
                    task,
                    OperationTaskStatusEnum.FAILED,
                    buildFailureReason(firstFailedStep)
            );
            return null;
        }

        // ======== 5. 当前阶段成功，查询下一阶段 ========

        Integer nextStepNo = operationTaskStepMapper.selectNextStepNo(
                taskNo,
                stepNo
        );
        if (nextStepNo != null) {
            return nextStepNo;
        }

        // ======== 6. 最后阶段成功，提交最终业务状态 ========

        completeVersionUpgradeIfNecessary(task);
        serverSwitchCompletionService.completeIfNecessary(task);

        // 最终业务状态与总任务SUCCESS在当前事务中一起提交。
        saveTaskStatus(task, OperationTaskStatusEnum.SUCCESS, null);
        return null;
    }

    /**
     * 版本升级成功后，将三台Redis实例的版本号更新为目标版本。
     */
    private void completeVersionUpgradeIfNecessary(OperationTask task) {

        // 不是版本升级任务，直接返回。
        if (!OperationTaskTypeEnum.VERSION_UPGRADE.name()
                .equals(task.getTaskType())) {
            return;
        }

        VersionUpgradeRequestDTO request;
        try {
            // 获取创建版本升级任务时保存的前端请求参数。
            request = JSON.parseObject(
                    task.getRequestParams(),
                    VersionUpgradeRequestDTO.class
            );
            if (request == null || request.getPackageId() == null) {
                throw new IllegalArgumentException();
            }
        } catch (Exception exception) {
            throw new BusinessException(
                    BusinessCode.OPERATION_ERROR.getCode(),
                    "版本升级任务参数无法解析"
            );
        }

        // 根据安装包记录取得本次升级的目标版本。
        RedisVersionPackage versionPackage = versionPackageMapper.selectById(
                request.getPackageId()
        );
        if (versionPackage == null) {
            throw new BusinessException(
                    BusinessCode.OPERATION_ERROR.getCode(),
                    "版本升级任务使用的安装包记录不存在"
            );
        }
        String targetVersion = versionPackage.getRedisVersion();

        // 查询当前正式纳管的三台Redis实例。
        List<RedisInstance> redisInstances = redisInstanceService.list(
                new LambdaQueryWrapper<RedisInstance>()
                        .eq(RedisInstance::getManagementStatus,
                                StatusEnum.ACTIVE.getCode())
        );
        if (redisInstances.size()
                != OperationTaskConstants.CURRENT_TOPOLOGY_NODE_COUNT) {
            throw new BusinessException(
                    BusinessCode.OPERATION_ERROR.getCode(),
                    "当前已纳管Redis记录不是3条"
            );
        }

        // 更新三台Redis的版本号，任意一条失败都会回滚本次最终提交。
        LocalDateTime now = LocalDateTime.now();
        for (RedisInstance redisInstance : redisInstances) {
            redisInstance.setRedisVersion(targetVersion);
            redisInstance.setUpdatedTime(now);
        }
        if (!redisInstanceService.updateBatchById(redisInstances)) {
            throw new BusinessException(
                    BusinessCode.OPERATION_ERROR.getCode(),
                    "Redis实例版本号更新失败"
            );
        }

        // 清理节点相关缓存，页面下一次查询直接读取新版本号。
        caffeineCache.invalidate(CacheKeyUtil.instanceList(
                StatusEnum.ACTIVE.getCode()
        ));
        caffeineCache.invalidate(CacheKeyUtil.TOPOLOGY);
        caffeineCache.invalidate(CacheKeyUtil.NODES);
    }

    @Override
    @Transactional
    public void markTaskResultUnknown(String taskNo, String reason) {

        OperationTask task = operationTaskMapper.selectTaskForUpdate(taskNo);
        if (task == null) {
            throw new BusinessException(
                    BusinessCode.NOT_FOUND_ERROR.getCode(),
                    "未找到需要更新状态的运维任务"
            );
        }

        if (!OperationTaskStatusEnum.RUNNING.name()
                .equals(task.getTaskStatus())) {
            return;
        }

        saveTaskStatus(
                task,
                OperationTaskStatusEnum.RESULT_UNKNOWN,
                reason
        );
    }

    /**
     * 人工结束结果未知的运维任务。
     */
    @Override
    @Transactional
    public void manuallyTerminateTask(
            String taskNo,
            String terminationReason,
            String terminatedBy) {

        // ======== 1. 锁定总任务 ========

        OperationTask task = operationTaskMapper.selectTaskForUpdate(taskNo);
        if (task == null) {
            throw new BusinessException(
                    BusinessCode.NOT_FOUND_ERROR.getCode(),
                    "未找到需要人工结束的运维任务"
            );
        }

        // ======== 2. 处理重复提交 ========

        if (OperationTaskStatusEnum.MANUALLY_TERMINATED.name()
                .equals(task.getTaskStatus())) {
            boolean sameReason = terminationReason.equals(
                    task.getTerminationReason()
            );
            boolean sameOperator = terminatedBy.equals(
                    task.getTerminatedBy()
            );
            if (sameReason && sameOperator) {
                return;
            }
            throw new BusinessException(
                    BusinessCode.OPERATION_ERROR.getCode(),
                    "当前运维任务已经被人工结束"
            );
        }

        // ======== 3. 校验任务状态 ========

        if (!OperationTaskStatusEnum.RESULT_UNKNOWN.name()
                .equals(task.getTaskStatus())) {
            throw new BusinessException(
                    BusinessCode.OPERATION_ERROR.getCode(),
                    "只有结果未知的运维任务允许人工结束"
            );
        }

        // ======== 4. 保存人工结束状态和审计信息 ========

        LocalDateTime now = LocalDateTime.now();
        task.setTaskStatus(
                OperationTaskStatusEnum.MANUALLY_TERMINATED.name()
        );
        task.setTerminationReason(terminationReason);
        task.setTerminatedBy(terminatedBy);
        task.setTerminatedTime(now);
        task.setEndTime(now);
        task.setUpdatedTime(now);

        int updated = operationTaskMapper.updateTaskManualTermination(task);
        if (updated != 1) {
            throw new BusinessException(
                    BusinessCode.OPERATION_ERROR.getCode(),
                    "人工结束运维任务失败"
            );
        }

        // 记录人工结束任务的审计日志。
        recordManualTerminationAudit(task);
    }

    /**
     * 锁定总任务并判断步骤是否仍允许变更。
     */
    private boolean taskAllowsStepUpdate(String taskNo) {

        OperationTask task = operationTaskMapper.selectTaskForUpdate(taskNo);
        if (task == null) {
            throw new BusinessException(
                    BusinessCode.NOT_FOUND_ERROR.getCode(),
                    "未找到需要更新步骤状态的运维任务"
            );
        }

        return OperationTaskStatusEnum.RUNNING.name()
                .equals(task.getTaskStatus());
    }

    /**
     * 条件更新失败时，确认是否已经被快速回调推进到终态。
     */
    private void validateStepAlreadyFinished(
            String taskNo,
            Integer stepNo,
            String executorId) {

        OperationTaskStep step =
                operationTaskStepMapper.selectTaskStepForUpdate(
                        taskNo,
                        stepNo,
                        executorId
                );
        if (step == null) {
            throw new BusinessException(
                    BusinessCode.NOT_FOUND_ERROR.getCode(),
                    "未找到需要更新下发状态的任务步骤"
            );
        }

        // 已经进入终态，说明已经收到执行器的快速回调。
        if (isTerminalStepStatus(step.getStepStatus())) {
            return;
        }

        throw new BusinessException(
                BusinessCode.OPERATION_ERROR.getCode(),
                "任务步骤下发状态发生冲突，当前状态："
                        + step.getStepStatus()
        );
    }

    private boolean isTerminalStepStatus(String status) {
        return OperationTaskStepStatusEnum.SUCCESS.name().equals(status)
                || OperationTaskStepStatusEnum.FAILED.name().equals(status)
                || OperationTaskStepStatusEnum.RESULT_UNKNOWN.name()
                .equals(status);
    }

    /**
     * 保存总任务状态及对应时间。
     */
    private void saveTaskStatus(
            OperationTask task,
            OperationTaskStatusEnum targetStatus,
            String failureReason) {

        LocalDateTime now = LocalDateTime.now();

        task.setTaskStatus(targetStatus.name());
        // 没有失败原因时保存空串，避免字符串字段写入null。
        task.setFailureReason(
                failureReason == null ? "" : failureReason
        );
        task.setUpdatedTime(now);

        if (OperationTaskStatusEnum.RUNNING == targetStatus) {
            if (task.getStartTime() == null) {
                task.setStartTime(now);
            }
            task.setEndTime(null);
        } else if (OperationTaskStatusEnum.RESULT_UNKNOWN == targetStatus) {
            if (task.getStartTime() == null) {
                task.setStartTime(now);
            }
            // 结果未知仍需人工确认，人工结束之前不记录结束时间。
            task.setEndTime(null);
        } else {
            if (task.getStartTime() == null) {
                task.setStartTime(now);
            }
            task.setEndTime(now);
        }

        int updated = operationTaskMapper.updateTaskExecutionState(task);
        if (updated != 1) {
            throw new BusinessException(
                    BusinessCode.OPERATION_ERROR.getCode(),
                    "运维任务状态保存失败"
            );
        }

        // 总任务成功或失败后记录审计日志。
        if (OperationTaskStatusEnum.SUCCESS == targetStatus
                || OperationTaskStatusEnum.FAILED == targetStatus) {
            recordTaskCompletionAudit(task, targetStatus);
        }
    }

    /**
     * 记录总任务成功或失败的审计日志。
     */
    private void recordTaskCompletionAudit(
            OperationTask task,
            OperationTaskStatusEnum taskStatus) {

        String operationType = getAuditOperationType(task.getTaskType());
        String operationResult;
        String content;

        if (OperationTaskStatusEnum.SUCCESS == taskStatus) {
            operationResult = "成功";
            content = operationType
                    + "任务执行成功，任务编号："
                    + task.getTaskNo()
                    + "，操作原因："
                    + task.getOperationReason();
        } else {
            operationResult = "失败";
            content = operationType
                    + "任务执行失败，任务编号："
                    + task.getTaskNo()
                    + "，失败原因："
                    + task.getFailureReason();
        }

        String operator = task.getCreatedBy();
        auditLogService.record(
                operator,
                operator,
                CommonConstant.MODULE_OPERATIONS,
                operationType,
                task.getTaskNo(),
                content,
                operationResult
        );
    }

    /**
     * 记录人工结束任务的审计日志。
     */
    private void recordManualTerminationAudit(OperationTask task) {
        String operationType = getAuditOperationType(task.getTaskType());
        String content = operationType
                + "任务已人工结束，任务编号："
                + task.getTaskNo()
                + "，终止原因："
                + task.getTerminationReason();

        auditLogService.record(
                task.getTerminatedBy(),
                task.getTerminatedBy(),
                CommonConstant.MODULE_OPERATIONS,
                operationType,
                task.getTaskNo(),
                content,
                "人工终止"
        );
    }

    /**
     * 获取运维任务对应的审计操作类型。
     */
    private String getAuditOperationType(String taskType) {
        if (OperationTaskTypeEnum.FAULT_RECOVERY.name().equals(taskType)) {
            return CommonConstant.OPERATION_TYPE_FAULT_RECOVERY;
        }
        if (OperationTaskTypeEnum.VERSION_UPGRADE.name().equals(taskType)) {
            return CommonConstant.OPERATION_TYPE_VERSION_UPGRADE;
        }
        if (OperationTaskTypeEnum.SERVER_SWITCH.name().equals(taskType)) {
            return CommonConstant.OPERATION_TYPE_SERVER_SWITCH;
        }
        return taskType;
    }

    private OperationResultDTO parseOperationResult(String resultJson) {
        try {
            return JSON.parseObject(resultJson, OperationResultDTO.class);
        } catch (Exception exception) {
            throw new BusinessException(
                    BusinessCode.PARAMS_ERROR.getCode(),
                    "任务执行结果内容无法解析"
            );
        }
    }

    private void fillTaskStepResult(
            OperationTaskStep step,
            TaskResultReportDTO request,
            OperationResultDTO result) {

        String errorCode = result.getErrorCode();
        if (errorCode == null) {
            errorCode = "";
        }

        String failureReason = "";
        if (!result.getSuccess()) {
            failureReason = result.getMessage();
            if (failureReason == null) {
                failureReason = "";
            }
        }

        step.setStepStatus(request.getStatus());
        step.setResultJson(request.getResult());
        step.setErrorCode(errorCode);
        step.setFailureReason(failureReason);
        step.setStartTime(toLocalDateTime(result.getStartTime()));
        step.setEndTime(toLocalDateTime(result.getEndTime()));
        step.setUpdatedTime(LocalDateTime.now());
    }

    private String buildDispatchFailureReason(
            Integer stepNo,
            String executorId,
            String reason) {
        if (reason == null || reason.trim().isEmpty()) {
            reason = "执行器拒绝接收任务";
        }
        return "阶段"
                + stepNo
                + "下发失败，执行器："
                + executorId
                + "，原因："
                + reason;
    }

    private String buildDispatchUnknownReason(OperationTaskStep step) {
        String reason = step.getFailureReason();
        if (reason == null || reason.trim().isEmpty()) {
            reason = "无法确认执行器是否接收任务";
        }
        return "阶段"
                + step.getStepNo()
                + "下发结果未知，执行器："
                + step.getExecutorId()
                + "，原因："
                + reason;
    }

    private String buildFailureReason(OperationTaskStep step) {
        String reason = step.getFailureReason();
        if (reason == null || reason.trim().isEmpty()) {
            reason = "执行器未返回失败原因";
        }
        return "阶段"
                + step.getStepNo()
                + "执行失败，执行器："
                + step.getExecutorId()
                + "，原因："
                + reason;
    }

    private String buildResultUnknownReason(OperationTaskStep step) {
        String reason = step.getFailureReason();
        if (reason == null || reason.trim().isEmpty()) {
            reason = "执行器未在规定时间内返回结果";
        }
        return "阶段"
                + step.getStepNo()
                + "执行结果未知，执行器："
                + step.getExecutorId()
                + "，原因："
                + reason;
    }

    private LocalDateTime toLocalDateTime(Long timestamp) {
        return LocalDateTime.ofInstant(
                Instant.ofEpochMilli(timestamp),
                PLATFORM_ZONE_ID
        );
    }
}


