package com.redisops.platform.operation.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redisops.platform.operation.model.CreateOperationTaskRequest;
import com.redisops.platform.operation.model.CreateOperationTaskStepRequest;
import com.redisops.platform.operation.model.OperationTaskDetail;
import com.redisops.platform.operation.model.TaskResultReportRequest;
import com.redisops.platform.agent.entity.ExecutorInfo;
import com.redisops.platform.operation.entity.OperationTask;
import com.redisops.platform.operation.entity.OperationTaskStep;
import com.redisops.platform.operation.mapper.OperationTaskMapper;
import com.redisops.platform.operation.mapper.OperationTaskStepMapper;
import com.redisops.platform.operation.type.AgentOperationType;
import com.redisops.platform.operation.type.BusinessTaskType;
import com.redisops.platform.operation.client.AgentTaskClient;
import com.redisops.platform.agent.service.AgentRegistrationService;
import com.redisops.platform.operation.service.OperationTaskService;
import com.redisops.platform.operation.service.OperationTaskRollbackPlanner;
import com.redisops.platform.operation.service.TaskNoService;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Date;

/**
 * 创建、下发并汇总运维任务步骤。
 */
@Service
public class OperationTaskServiceImpl implements OperationTaskService {

    private static final int ROLLBACK_STEP_START = 1001;

    private final OperationTaskMapper taskMapper;
    private final OperationTaskStepMapper stepMapper;
    private final TaskNoService taskNoService;
    private final AgentRegistrationService agentRegistrationService;
    private final AgentTaskClient agentTaskClient;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final TaskScheduler operationTaskScheduler;
    private final List<OperationTaskRollbackPlanner> rollbackPlanners;

    /** 注入任务持久化、执行器访问和事务组件。 */
    public OperationTaskServiceImpl(OperationTaskMapper taskMapper,
                                    OperationTaskStepMapper stepMapper,
                                    TaskNoService taskNoService,
                                    AgentRegistrationService agentRegistrationService,
                                    AgentTaskClient agentTaskClient,
                                    ObjectMapper objectMapper,
                                    TransactionTemplate transactionTemplate,
                                    @Qualifier("operationTaskScheduler")
                                    TaskScheduler operationTaskScheduler,
                                    List<OperationTaskRollbackPlanner> rollbackPlanners) {
        this.taskMapper = taskMapper;
        this.stepMapper = stepMapper;
        this.taskNoService = taskNoService;
        this.agentRegistrationService = agentRegistrationService;
        this.agentTaskClient = agentTaskClient;
        this.objectMapper = objectMapper;
        this.transactionTemplate = transactionTemplate;
        this.operationTaskScheduler = operationTaskScheduler;
        this.rollbackPlanners = rollbackPlanners;
    }

    /**
     * 创建任务和全部步骤，事务提交后只下发第一个步骤组。
     *
     * @param request 创建任务请求
     * @return 创建并下发后的任务详情
     */
    @Override
    public synchronized OperationTaskDetail createAndDispatch(CreateOperationTaskRequest request) {
        validateCreateRequest(request);

        OperationTaskDetail created = transactionTemplate.execute(status -> persistTask(request));
        if (created == null) {
            throw new IllegalStateException("任务创建事务没有返回结果");
        }

        dispatchNextStepGroup(created.getTask().getTaskNo());
        return getTaskDetail(created.getTask().getTaskNo());
    }

    /**
     * 接收一台执行器的步骤结果，并在步骤组全部成功后推进下一组。
     *
     * @param authorization 执行器 Bearer Token
     * @param request 结果上报内容
     */
    @Override
    public synchronized void receiveResult(String authorization, TaskResultReportRequest request) {
        validateResultRequest(request);
        agentRegistrationService.authenticateExecutor(request.getAgentId(), authorization);

        OperationTaskStep step = findReportedStep(request);
        if (step == null) {
            throw new IllegalArgumentException("没有找到与回调匹配的任务步骤");
        }
        if (!step.getOperationType().equals(request.getOperationType())) {
            throw new IllegalArgumentException("回调 operationType 与任务步骤不一致");
        }

        // 执行器可能因网络重试重复上报相同结果，已经结束的步骤按幂等成功处理。
        if ("SUCCESS".equals(step.getStepStatus()) || "FAILED".equals(step.getStepStatus())) {
            return;
        }
        if (!"DISPATCHED".equals(step.getStepStatus())) {
            throw new IllegalStateException("当前步骤状态不允许接收结果: " + step.getStepStatus());
        }

        boolean success = "SUCCESS".equals(request.getStatus());
        ResultSummary summary = parseResult(request.getResult());
        LocalDateTime now = LocalDateTime.now();
        LambdaUpdateWrapper<OperationTaskStep> update = new LambdaUpdateWrapper<>();
        update.eq(OperationTaskStep::getId, step.getId())
                .eq(OperationTaskStep::getStepStatus, "DISPATCHED")
                .set(OperationTaskStep::getStepStatus, success ? "SUCCESS" : "FAILED")
                .set(OperationTaskStep::getResultJson, summary.resultJson)
                .set(OperationTaskStep::getErrorCode, summary.errorCode)
                .set(OperationTaskStep::getFailureReason, success ? null : summary.message)
                .set(OperationTaskStep::getEndTime, now);
        int changed = stepMapper.update(null, update);
        if (changed == 0) {
            return;
        }
        agentRegistrationService.touchLastReportTime(request.getAgentId());

        if (!success) {
            if (isRollbackStep(step)) {
                markTaskFailed(request.getTaskNo(),
                        "自动回滚失败: " + safeReason(summary.message));
            } else {
                beginFailureHandling(request.getTaskNo(), summary.message);
            }
            return;
        }
        scheduleAdvance(request.getTaskNo());
    }

    /**
     * 查询任务主记录和所有步骤。
     *
     * @param taskNo 任务编号
     * @return 任务详情
     */
    @Override
    public OperationTaskDetail getTaskDetail(String taskNo) {
        OperationTask task = taskMapper.selectOne(new LambdaQueryWrapper<OperationTask>()
                .eq(OperationTask::getTaskNo, taskNo));
        if (task == null) {
            throw new IllegalArgumentException("任务不存在: " + taskNo);
        }
        List<OperationTaskStep> steps = stepMapper.selectList(
                new LambdaQueryWrapper<OperationTaskStep>()
                        .eq(OperationTaskStep::getTaskNo, taskNo)
                        .orderByAsc(OperationTaskStep::getStepNo)
                        .orderByAsc(OperationTaskStep::getExecutorId));
        return new OperationTaskDetail(task, steps);
    }

    /** 在一个数据库事务中写入任务主记录和全部步骤。 */
    private OperationTaskDetail persistTask(CreateOperationTaskRequest request) {
        long activeCount = taskMapper.selectCount(new LambdaQueryWrapper<OperationTask>()
                .in(OperationTask::getTaskStatus,
                        "CREATED", "RUNNING", "ROLLING_BACK"));
        if (activeCount > 0) {
            throw new IllegalStateException("当前已有正在进行的高风险运维任务");
        }

        String taskNo = taskNoService.nextTaskNo();
        OperationTask task = new OperationTask();
        task.setTaskNo(taskNo);
        task.setTaskType(request.getTaskType().trim());
        task.setTaskStatus("CREATED");
        task.setRequestParams(writeJson(request.getRequestParams()));
        task.setCreatedBy(request.getCreatedBy().trim());
        task.setOperationReason(request.getOperationReason().trim());
        taskMapper.insert(task);

        List<OperationTaskStep> steps = new ArrayList<>();
        for (CreateOperationTaskStepRequest requestedStep : request.getSteps()) {
            OperationTaskStep step = new OperationTaskStep();
            step.setTaskNo(taskNo);
            step.setStepNo(requestedStep.getStepNo());
            step.setExecutorId(requestedStep.getExecutorId().trim());
            step.setOperationType(requestedStep.getOperationType().trim());
            step.setStepStatus("CREATED");
            step.setRequestParams(writeJson(requestedStep.getParams()));
            step.setDispatchCount(0);
            stepMapper.insert(step);
            steps.add(step);
        }
        return new OperationTaskDetail(task, steps);
    }

    /** 原子认领并下发编号最小的 CREATED 步骤组。 */
    private void dispatchNextStepGroup(String taskNo) {
        OperationTask task = taskMapper.selectOne(new LambdaQueryWrapper<OperationTask>()
                .eq(OperationTask::getTaskNo, taskNo));
        if (task == null || isFinalTaskStatus(task.getTaskStatus())) {
            return;
        }

        List<OperationTaskStep> createdSteps = stepMapper.selectList(
                new LambdaQueryWrapper<OperationTaskStep>()
                        .eq(OperationTaskStep::getTaskNo, taskNo)
                        .eq(OperationTaskStep::getStepStatus, "CREATED")
                        .orderByAsc(OperationTaskStep::getStepNo)
                        .orderByAsc(OperationTaskStep::getExecutorId));
        if (createdSteps.isEmpty()) {
            markTaskSuccessIfAllStepsFinished(taskNo);
            return;
        }

        int nextStepNo = createdSteps.get(0).getStepNo();
        markTaskRunning(taskNo);
        for (OperationTaskStep step : createdSteps) {
            if (step.getStepNo() != nextStepNo) {
                break;
            }
            if (!claimStep(step)) {
                continue;
            }
            try {
                ExecutorInfo executor = agentRegistrationService
                        .requireOnlineExecutor(step.getExecutorId());
                agentTaskClient.dispatch(executor, taskNo, step.getStepNo(),
                        step.getOperationType(), step.getRequestParams());
            } catch (Exception e) {
                String reason = shorten("步骤下发失败: " + rootMessage(e), 1000);
                markStepDispatchFailed(step.getId(), reason);
                if (isRollbackStep(step)) {
                    markTaskFailed(taskNo, "自动回滚失败: " + reason);
                } else {
                    beginFailureHandling(taskNo, reason);
                }
                break;
            }
        }
    }

    /** 把 CREATED 步骤原子修改为 DISPATCHED，防止并发回调造成重复下发。 */
    private boolean claimStep(OperationTaskStep step) {
        LocalDateTime now = LocalDateTime.now();
        LambdaUpdateWrapper<OperationTaskStep> update = new LambdaUpdateWrapper<>();
        update.eq(OperationTaskStep::getId, step.getId())
                .eq(OperationTaskStep::getStepStatus, "CREATED")
                .set(OperationTaskStep::getStepStatus, "DISPATCHED")
                .set(OperationTaskStep::getStartTime, now)
                .set(OperationTaskStep::getLastDispatchTime, now)
                .setSql("dispatch_count = dispatch_count + 1");
        return stepMapper.update(null, update) == 1;
    }

    /** 当当前步骤组全部成功时，下发下一组；没有下一组时完成任务。 */
    private synchronized void advanceAfterSuccessfulCallback(String taskNo) {
        OperationTask task = findTask(taskNo);
        if (task == null || isFinalTaskStatus(task.getTaskStatus())) {
            return;
        }
        List<OperationTaskStep> allSteps = stepMapper.selectList(
                new LambdaQueryWrapper<OperationTaskStep>()
                        .eq(OperationTaskStep::getTaskNo, taskNo));
        if ("ROLLING_BACK".equals(task.getTaskStatus())) {
            advanceRollback(task, allSteps);
            return;
        }
        for (OperationTaskStep step : allSteps) {
            if ("FAILED".equals(step.getStepStatus())) {
                beginFailureHandling(taskNo, step.getFailureReason());
                return;
            }
            if ("DISPATCHED".equals(step.getStepStatus())) {
                return;
            }
        }
        dispatchNextStepGroup(taskNo);
    }

    /**
     * 在回调响应返回后再推进下一步骤组，让执行器先释放本机单线程。
     *
     * @param taskNo 业务任务编号
     */
    private void scheduleAdvance(String taskNo) {
        Date executeTime = new Date(System.currentTimeMillis() + 1000L);
        operationTaskScheduler.schedule(
                () -> advanceAfterSuccessfulCallback(taskNo), executeTime);
    }

    /** 当前所有步骤成功时把任务更新为 SUCCESS。 */
    private void markTaskSuccessIfAllStepsFinished(String taskNo) {
        OperationTask task = findTask(taskNo);
        if (task == null || isFinalTaskStatus(task.getTaskStatus())) {
            return;
        }
        if ("ROLLING_BACK".equals(task.getTaskStatus())) {
            markTaskRolledBackIfFinished(taskNo);
            return;
        }
        long unfinished = stepMapper.selectCount(new LambdaQueryWrapper<OperationTaskStep>()
                .eq(OperationTaskStep::getTaskNo, taskNo)
                .ne(OperationTaskStep::getStepStatus, "SUCCESS"));
        if (unfinished == 0) {
            LambdaUpdateWrapper<OperationTask> update = new LambdaUpdateWrapper<>();
            update.eq(OperationTask::getTaskNo, taskNo)
                    .in(OperationTask::getTaskStatus, "CREATED", "RUNNING")
                    .set(OperationTask::getTaskStatus, "SUCCESS")
                    .set(OperationTask::getEndTime, LocalDateTime.now());
            taskMapper.update(null, update);
        }
    }

    /** 第一次下发步骤时把任务改为 RUNNING 并记录开始时间。 */
    private void markTaskRunning(String taskNo) {
        LambdaUpdateWrapper<OperationTask> update = new LambdaUpdateWrapper<>();
        update.eq(OperationTask::getTaskNo, taskNo)
                .eq(OperationTask::getTaskStatus, "CREATED")
                .set(OperationTask::getTaskStatus, "RUNNING")
                .set(OperationTask::getStartTime, LocalDateTime.now());
        taskMapper.update(null, update);
    }

    /** 记录步骤在发送给执行器之前发生的失败。 */
    private void markStepDispatchFailed(Long stepId, String reason) {
        LambdaUpdateWrapper<OperationTaskStep> update = new LambdaUpdateWrapper<>();
        update.eq(OperationTaskStep::getId, stepId)
                .eq(OperationTaskStep::getStepStatus, "DISPATCHED")
                .set(OperationTaskStep::getStepStatus, "FAILED")
                .set(OperationTaskStep::getErrorCode, "DISPATCH_FAILED")
                .set(OperationTaskStep::getFailureReason, reason)
                .set(OperationTaskStep::getEndTime, LocalDateTime.now());
        stepMapper.update(null, update);
    }

    /** 把任务更新为 FAILED，并保存适合展示的失败原因。 */
    private void markTaskFailed(String taskNo, String reason) {
        LambdaUpdateWrapper<OperationTask> update = new LambdaUpdateWrapper<>();
        update.eq(OperationTask::getTaskNo, taskNo)
                .notIn(OperationTask::getTaskStatus, "SUCCESS", "FAILED", "ROLLED_BACK")
                .set(OperationTask::getTaskStatus, "FAILED")
                .set(OperationTask::getFailureReason, shorten(reason, 1000))
                .set(OperationTask::getEndTime, LocalDateTime.now());
        taskMapper.update(null, update);
    }

    /**
     * 正常步骤失败后选择对应业务的回滚规划器；没有规划器的任务直接失败。
     */
    private void beginFailureHandling(String taskNo, String reason) {
        OperationTask task = findTask(taskNo);
        if (task == null || isFinalTaskStatus(task.getTaskStatus())) {
            return;
        }
        if ("ROLLING_BACK".equals(task.getTaskStatus())) {
            scheduleAdvance(taskNo);
            return;
        }

        OperationTaskRollbackPlanner planner = findRollbackPlanner(task);
        if (planner == null) {
            markTaskFailed(taskNo, reason);
            return;
        }

        LambdaUpdateWrapper<OperationTask> taskUpdate = new LambdaUpdateWrapper<>();
        taskUpdate.eq(OperationTask::getTaskNo, taskNo)
                .in(OperationTask::getTaskStatus, "CREATED", "RUNNING")
                .set(OperationTask::getTaskStatus, "ROLLING_BACK")
                .set(OperationTask::getFailureReason, shorten(safeReason(reason), 1000))
                .set(OperationTask::getEndTime, null);
        taskMapper.update(null, taskUpdate);

        // 尚未下发的正常步骤必须取消，防止回滚时又继续推进新版本启动流程。
        LambdaUpdateWrapper<OperationTaskStep> stepUpdate = new LambdaUpdateWrapper<>();
        stepUpdate.eq(OperationTaskStep::getTaskNo, taskNo)
                .lt(OperationTaskStep::getStepNo, ROLLBACK_STEP_START)
                .eq(OperationTaskStep::getStepStatus, "CREATED")
                .set(OperationTaskStep::getStepStatus, "CANCELLED")
                .set(OperationTaskStep::getEndTime, LocalDateTime.now());
        stepMapper.update(null, stepUpdate);
        scheduleAdvance(taskNo);
    }

    /**
     * 等待失败步骤组中已经下发的其他节点全部回报，再生成并调度回滚步骤。
     */
    private void advanceRollback(OperationTask task, List<OperationTaskStep> allSteps) {
        for (OperationTaskStep step : allSteps) {
            if (isRollbackStep(step) && "FAILED".equals(step.getStepStatus())) {
                markTaskFailed(task.getTaskNo(),
                        "自动回滚失败: " + safeReason(step.getFailureReason()));
                return;
            }
            if ("DISPATCHED".equals(step.getStepStatus())) {
                return;
            }
        }

        if (containsRollbackSteps(allSteps)) {
            dispatchNextStepGroup(task.getTaskNo());
            return;
        }

        OperationTaskRollbackPlanner planner = findRollbackPlanner(task);
        if (planner == null) {
            markTaskFailed(task.getTaskNo(), task.getFailureReason());
            return;
        }
        try {
            List<CreateOperationTaskStepRequest> rollbackSteps =
                    planner.buildRollbackSteps(task, allSteps);
            if (rollbackSteps == null || rollbackSteps.isEmpty()) {
                // 版本包部署阶段尚未影响线上服务，不需要执行补偿操作。
                markTaskFailed(task.getTaskNo(), task.getFailureReason());
                return;
            }
            insertRollbackSteps(task.getTaskNo(), rollbackSteps);
            dispatchNextStepGroup(task.getTaskNo());
        } catch (Exception e) {
            markTaskFailed(task.getTaskNo(),
                    "生成或保存自动回滚计划失败: " + rootMessage(e));
        }
    }

    /** 把业务规划器生成的回滚步骤写入原任务。 */
    private void insertRollbackSteps(String taskNo,
                                     List<CreateOperationTaskStepRequest> rollbackSteps) {
        for (CreateOperationTaskStepRequest requestedStep : rollbackSteps) {
            if (requestedStep.getStepNo() == null
                    || requestedStep.getStepNo() < ROLLBACK_STEP_START) {
                throw new IllegalArgumentException("回滚 stepNo 必须大于等于 "
                        + ROLLBACK_STEP_START);
            }
            AgentOperationType.fromValue(requestedStep.getOperationType());
            agentRegistrationService.requireOnlineExecutor(requestedStep.getExecutorId());

            OperationTaskStep step = new OperationTaskStep();
            step.setTaskNo(taskNo);
            step.setStepNo(requestedStep.getStepNo());
            step.setExecutorId(requestedStep.getExecutorId().trim());
            step.setOperationType(requestedStep.getOperationType().trim());
            step.setStepStatus("CREATED");
            step.setRequestParams(writeJson(requestedStep.getParams()));
            step.setDispatchCount(0);
            stepMapper.insert(step);
        }
    }

    /** 回滚步骤全部成功时把任务标记为 ROLLED_BACK。 */
    private void markTaskRolledBackIfFinished(String taskNo) {
        long rollbackCount = stepMapper.selectCount(
                new LambdaQueryWrapper<OperationTaskStep>()
                        .eq(OperationTaskStep::getTaskNo, taskNo)
                        .ge(OperationTaskStep::getStepNo, ROLLBACK_STEP_START));
        long unfinished = stepMapper.selectCount(
                new LambdaQueryWrapper<OperationTaskStep>()
                        .eq(OperationTaskStep::getTaskNo, taskNo)
                        .ge(OperationTaskStep::getStepNo, ROLLBACK_STEP_START)
                        .ne(OperationTaskStep::getStepStatus, "SUCCESS"));
        if (rollbackCount > 0 && unfinished == 0) {
            LambdaUpdateWrapper<OperationTask> update = new LambdaUpdateWrapper<>();
            update.eq(OperationTask::getTaskNo, taskNo)
                    .eq(OperationTask::getTaskStatus, "ROLLING_BACK")
                    .set(OperationTask::getTaskStatus, "ROLLED_BACK")
                    .set(OperationTask::getEndTime, LocalDateTime.now());
            taskMapper.update(null, update);
        }
    }

    /** 查找负责当前业务任务的回滚规划器。 */
    private OperationTaskRollbackPlanner findRollbackPlanner(OperationTask task) {
        for (OperationTaskRollbackPlanner planner : rollbackPlanners) {
            if (planner.supports(task)) {
                return planner;
            }
        }
        return null;
    }

    /** 根据任务编号查询任务主记录。 */
    private OperationTask findTask(String taskNo) {
        return taskMapper.selectOne(new LambdaQueryWrapper<OperationTask>()
                .eq(OperationTask::getTaskNo, taskNo));
    }

    /** 判断步骤是否属于动态追加的回滚阶段。 */
    private boolean isRollbackStep(OperationTaskStep step) {
        return step != null && step.getStepNo() != null
                && step.getStepNo() >= ROLLBACK_STEP_START;
    }

    /** 判断任务是否已经处于不能再推进的最终状态。 */
    private boolean isFinalTaskStatus(String status) {
        return "SUCCESS".equals(status) || "FAILED".equals(status)
                || "ROLLED_BACK".equals(status);
    }

    /** 判断任务是否已经生成至少一条回滚步骤。 */
    private boolean containsRollbackSteps(List<OperationTaskStep> steps) {
        for (OperationTaskStep step : steps) {
            if (isRollbackStep(step)) {
                return true;
            }
        }
        return false;
    }

    /** 把空失败原因转换为稳定的默认说明。 */
    private String safeReason(String reason) {
        return StringUtils.hasText(reason) ? reason : "执行器未返回失败原因";
    }

    /** 根据任务编号、步骤序号和执行器编号精确匹配回调步骤。 */
    private OperationTaskStep findReportedStep(TaskResultReportRequest request) {
        return stepMapper.selectOne(new LambdaQueryWrapper<OperationTaskStep>()
                .eq(OperationTaskStep::getTaskNo, request.getTaskNo())
                .eq(OperationTaskStep::getStepNo, request.getStepNo())
                .eq(OperationTaskStep::getExecutorId, request.getAgentId()));
    }

    /** 校验任务类型、步骤连续性、重复节点和执行器在线状态。 */
    private void validateCreateRequest(CreateOperationTaskRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("任务内容不能为空");
        }
        BusinessTaskType.fromValue(request.getTaskType());
        if (!StringUtils.hasText(request.getCreatedBy())) {
            throw new IllegalArgumentException("createdBy 不能为空");
        }
        if (!StringUtils.hasText(request.getOperationReason())) {
            throw new IllegalArgumentException("operationReason 不能为空");
        }
        if (request.getSteps() == null || request.getSteps().isEmpty()) {
            throw new IllegalArgumentException("任务至少需要一个执行步骤");
        }

        List<CreateOperationTaskStepRequest> sorted = new ArrayList<>(request.getSteps());
        sorted.sort(Comparator.comparing(CreateOperationTaskStepRequest::getStepNo,
                Comparator.nullsFirst(Integer::compareTo)));
        Set<String> uniqueTargets = new HashSet<>();
        Set<Integer> stepNumbers = new HashSet<>();
        for (CreateOperationTaskStepRequest step : sorted) {
            if (step == null || step.getStepNo() == null || step.getStepNo() < 1) {
                throw new IllegalArgumentException("每个 stepNo 都必须大于 0");
            }
            if (!StringUtils.hasText(step.getExecutorId())) {
                throw new IllegalArgumentException("每个步骤都必须指定 executorId");
            }
            AgentOperationType.fromValue(step.getOperationType());
            agentRegistrationService.requireOnlineExecutor(step.getExecutorId());
            String targetKey = step.getStepNo() + "|" + step.getExecutorId().trim();
            if (!uniqueTargets.add(targetKey)) {
                throw new IllegalArgumentException("同一步骤不能重复指定同一执行器: " + targetKey);
            }
            stepNumbers.add(step.getStepNo());
        }
        int maxStepNo = Collections.max(stepNumbers);
        for (int expected = 1; expected <= maxStepNo; expected++) {
            if (!stepNumbers.contains(expected)) {
                throw new IllegalArgumentException("stepNo 必须从 1 开始连续递增");
            }
        }
    }

    /** 校验执行结果回调的匹配字段和状态值。 */
    private void validateResultRequest(TaskResultReportRequest request) {
        if (request == null
                || !StringUtils.hasText(request.getTaskNo())
                || request.getStepNo() == null || request.getStepNo() < 1
                || !StringUtils.hasText(request.getAgentId())
                || !StringUtils.hasText(request.getOperationType())) {
            throw new IllegalArgumentException("taskNo、stepNo、agentId 和 operationType 不能为空");
        }
        if (!"SUCCESS".equals(request.getStatus()) && !"FAILED".equals(request.getStatus())) {
            throw new IllegalArgumentException("status 只能是 SUCCESS 或 FAILED");
        }
        AgentOperationType.fromValue(request.getOperationType());
    }

    /** 把对象序列化为可写入 MySQL JSON 字段的文本。 */
    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null
                    ? Collections.emptyMap() : value);
        } catch (Exception e) {
            throw new IllegalArgumentException("任务参数无法转换为 JSON", e);
        }
    }

    /** 校验执行器结果 JSON，并提取错误码和说明。 */
    private ResultSummary parseResult(String result) {
        try {
            String normalized = StringUtils.hasText(result) ? result : "{}";
            JsonNode root = objectMapper.readTree(normalized);
            String errorCode = textValue(root, "errorCode");
            String message = textValue(root, "message");
            return new ResultSummary(normalized, errorCode, message);
        } catch (Exception e) {
            throw new IllegalArgumentException("result 不是合法 JSON", e);
        }
    }

    /** 安全读取 JSON 中的字符串字段。 */
    private String textValue(JsonNode root, String fieldName) {
        JsonNode node = root == null ? null : root.get(fieldName);
        return node == null || node.isNull() ? null : node.asText();
    }

    /** 获取异常链最内层、最有用的错误说明。 */
    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return StringUtils.hasText(current.getMessage())
                ? current.getMessage() : current.getClass().getSimpleName();
    }

    /** 把数据库展示字段限制在允许长度内。 */
    private String shorten(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    /** 执行器结果 JSON 的内部解析结果。 */
    private static class ResultSummary {
        private final String resultJson;
        private final String errorCode;
        private final String message;

        /** 创建执行结果摘要。 */
        private ResultSummary(String resultJson, String errorCode, String message) {
            this.resultJson = resultJson;
            this.errorCode = errorCode;
            this.message = message;
        }
    }
}
