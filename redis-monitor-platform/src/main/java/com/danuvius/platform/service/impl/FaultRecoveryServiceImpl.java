package com.danuvius.platform.service.impl;

import com.alibaba.fastjson.JSON;
import com.danuvius.platform.common.constant.FaultRecoveryConstants;
import com.danuvius.platform.common.exception.BusinessCode;
import com.danuvius.platform.common.exception.BusinessException;
import com.danuvius.platform.model.dto.CurrentTopologyNode;
import com.danuvius.platform.model.dto.FaultRecoveryRequestDTO;
import com.danuvius.platform.model.entity.OperationTask;
import com.danuvius.platform.model.entity.OperationTaskStep;
import com.danuvius.platform.model.enums.ExecutorOperationTypeEnum;
import com.danuvius.platform.model.enums.OperationTaskStatusEnum;
import com.danuvius.platform.model.enums.OperationTaskStepStatusEnum;
import com.danuvius.platform.model.enums.OperationTaskTypeEnum;
import com.danuvius.platform.model.vo.FaultRecoveryResponseVO;
import com.danuvius.platform.service.CurrentTopologyService;
import com.danuvius.platform.service.OperationTaskDispatchExecutor;
import com.danuvius.platform.service.FaultRecoveryService;
import com.danuvius.platform.service.OperationTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 故障恢复服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FaultRecoveryServiceImpl implements FaultRecoveryService {

    private final CurrentTopologyService currentTopologyService;

    private final OperationTaskService operationTaskService;

    private final OperationTaskDispatchExecutor
            operationTaskDispatchExecutor;

    /**
     * 对当前纳管的Redis环境执行故障恢复。
     *
     * @param request 故障恢复请求
     * @return 故障恢复结果
     */
    @Override
    @Transactional
    public FaultRecoveryResponseVO faultRecovery(
            FaultRecoveryRequestDTO request) {

        // 1. 查询并校验故障恢复需要的环境信息。
        List<CurrentTopologyNode> nodes =
                currentTopologyService.loadCurrentNodes();

        // 2. 检查其他高风险任务并创建故障恢复任务。
        OperationTask task = operationTaskService.createTask(
                OperationTaskTypeEnum.FAULT_RECOVERY,
                request.getOperationReason(),
                request.getCreatedBy(),
                JSON.toJSONString(request)
        );

        // 3. 生成并保存本次故障恢复的全部执行步骤。
        List<OperationTaskStep> steps = buildRecoverySteps(
                task.getTaskNo(),
                nodes
        );
        operationTaskService.saveSteps(steps);

        // 4. 当前事务提交后，异步下发故障恢复的第一个阶段。避免任务步骤尚未成功保存，就开始向执行器下发。
        registerStageDispatchAfterCommit(
                task.getTaskNo(),
                FaultRecoveryConstants.STOP_SENTINEL_STEP_NO
        );

        // 5. 立即向页面返回任务信息，不等待执行器完成恢复。
        return new FaultRecoveryResponseVO(
                task.getTaskNo(),
                task.getTaskStatus(),
                operationTaskService.getCurrentStepNo(task.getTaskNo()),
                FaultRecoveryConstants.TASK_CREATED_MESSAGE,
                null,
                null,
                null,
                null
        );
    }

    /**
     * 查询故障恢复任务状态。
     *
     * @param taskNo 故障恢复任务编号
     * @return 故障恢复任务状态
     */
    @Override
    public FaultRecoveryResponseVO getFaultRecoveryStatus(String taskNo) {

        OperationTask task = operationTaskService.getTaskByTaskNo(taskNo);

        // 任务不存在或不是故障恢复任务时，统一按照未找到处理。
        if (task == null
                || !OperationTaskTypeEnum.FAULT_RECOVERY.name()
                .equals(task.getTaskType())) {
            throw new BusinessException(
                    BusinessCode.NOT_FOUND_ERROR.getCode(),
                    "未找到对应的故障恢复任务"
            );
        }

        return new FaultRecoveryResponseVO(
                task.getTaskNo(),
                task.getTaskStatus(),
                operationTaskService.getCurrentStepNo(task.getTaskNo()),
                buildTaskStatusMessage(task.getTaskStatus()),
                task.getFailureReason(),
                task.getTerminationReason(),
                task.getTerminatedBy(),
                task.getTerminatedTime()
        );
    }

    /**
     * 根据故障恢复任务状态生成页面提示语。
     */
    private String buildTaskStatusMessage(String taskStatus) {

        OperationTaskStatusEnum status;

        try {
            status = OperationTaskStatusEnum.valueOf(taskStatus);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new BusinessException(
                    BusinessCode.OPERATION_ERROR.getCode(),
                    "故障恢复任务状态不正确"
            );
        }

        switch (status) {
            case CREATED:
                return FaultRecoveryConstants.TASK_CREATED_MESSAGE;
            case RUNNING:
                return FaultRecoveryConstants.TASK_RUNNING_MESSAGE;
            case RESULT_UNKNOWN:
                return FaultRecoveryConstants.TASK_RESULT_UNKNOWN_MESSAGE;
            case MANUALLY_TERMINATED:
                return FaultRecoveryConstants
                        .TASK_MANUALLY_TERMINATED_MESSAGE;
            case SUCCESS:
                return FaultRecoveryConstants.TASK_SUCCESS_MESSAGE;
            case FAILED:
                return FaultRecoveryConstants.TASK_FAILED_MESSAGE;
            default:
                throw new BusinessException(
                        BusinessCode.OPERATION_ERROR.getCode(),
                        "故障恢复任务状态不正确"
                );
        }
    }

    /**
     * 注册事务提交后的阶段调度，避免任务步骤尚未落库就开始下发。
     */
    private void registerStageDispatchAfterCommit(
            String taskNo,
            Integer stepNo) {

        TransactionSynchronizationManager.registerSynchronization(

                // 创建一个事务回调对象。
                new TransactionSynchronization() {

                    // afterCommit()表示：当前数据库事务成功提交之后，执行这里的代码。
                    @Override
                    public void afterCommit() {
                        operationTaskDispatchExecutor.dispatchStage(
                                taskNo,
                                stepNo
                        );
                    }
                }
        );
    }

    /**
     * 为故障恢复任务生成全部执行步骤。
     *
     * @param taskNo 运维任务编号
     * @param nodes 当前正式纳管的运维节点
     * @return 故障恢复执行步骤
     */
    private List<OperationTaskStep> buildRecoverySteps(
            String taskNo,
            List<CurrentTopologyNode> nodes) {

        List<OperationTaskStep> steps = new ArrayList<>();

        // ======== 1. 停止三台Sentinel ========
        for (CurrentTopologyNode node : nodes) {
            steps.add(createRecoveryStep(
                    taskNo,
                    FaultRecoveryConstants.STOP_SENTINEL_STEP_NO,
                    node,
                    ExecutorOperationTypeEnum.STOP_SENTINEL,
                    null
            ));
        }

        // ======== 2. 停止三台Redis ========
        for (CurrentTopologyNode node : nodes) {
            steps.add(createRecoveryStep(
                    taskNo,
                    FaultRecoveryConstants.STOP_REDIS_STEP_NO,
                    node,
                    ExecutorOperationTypeEnum.STOP_REDIS,
                    null
            ));
        }

        // ======== 3. 恢复三台服务器上的配置文件 ========
        for (CurrentTopologyNode node : nodes) {
            // 将本机Redis和Sentinel备份配置的摘要传给执行器。
            // 执行器恢复前会再次校验本地备份，防止恢复错误文件。
            Map<String, String> restoreConfigParams = new HashMap<>();
            restoreConfigParams.put(
                    FaultRecoveryConstants.REDIS_CONFIG_SHA256_PARAM,
                    node.getRedisInstance().getConfigSha256()
            );
            restoreConfigParams.put(
                    FaultRecoveryConstants.SENTINEL_CONFIG_SHA256_PARAM,
                    node.getSentinelNode().getConfigSha256()
            );

            steps.add(createRecoveryStep(
                    taskNo,
                    FaultRecoveryConstants.RESTORE_CONFIG_STEP_NO,
                    node,
                    ExecutorOperationTypeEnum.RESTORE_CONFIG,
                    JSON.toJSONString(restoreConfigParams)
            ));
        }

        // ======== 4. 启动三台Redis ========
        for (CurrentTopologyNode node : nodes) {
            steps.add(createRecoveryStep(
                    taskNo,
                    FaultRecoveryConstants.START_REDIS_STEP_NO,
                    node,
                    ExecutorOperationTypeEnum.START_REDIS,
                    null
            ));
        }

        // ======== 5. 启动三台Sentinel ========
        for (CurrentTopologyNode node : nodes) {
            steps.add(createRecoveryStep(
                    taskNo,
                    FaultRecoveryConstants.START_SENTINEL_STEP_NO,
                    node,
                    ExecutorOperationTypeEnum.START_SENTINEL,
                    null
            ));
        }

        // ======== 6. 验证运维操作完成后的Redis和Sentinel拓扑 ========
        // 三台执行器分别检查本机Redis和Sentinel，并在拓扑满足要求后上报成功。
        for (CurrentTopologyNode node : nodes) {
            steps.add(createRecoveryStep(
                    taskNo,
                    FaultRecoveryConstants.VERIFY_TOPOLOGY_STEP_NO,
                    node,
                    ExecutorOperationTypeEnum.VERIFY_TOPOLOGY,
                    null
            ));
        }

        return steps;
    }

    /**
     * 创建单条故障恢复执行步骤。
     *
     * @param taskNo 运维任务编号
     * @param stepNo 阶段编号
     * @param node 当前运维节点
     * @param operationType 执行器操作类型
     * @param requestParams 任务参数JSON
     * @return 故障恢复执行步骤
     */
    private OperationTaskStep createRecoveryStep(
            String taskNo,
            Integer stepNo,
            CurrentTopologyNode node,
            ExecutorOperationTypeEnum operationType,
            String requestParams) {

        LocalDateTime now = LocalDateTime.now();
        OperationTaskStep step = new OperationTaskStep();
        step.setTaskNo(taskNo);
        step.setStepNo(stepNo);
        step.setExecutorId(node.getExecutorInfo().getId());
        step.setOperationType(operationType.name());
        step.setStepStatus(OperationTaskStepStatusEnum.CREATED.name());
        // 没有请求参数时保存空JSON对象，避免JSON字段写入null。
        step.setRequestParams(requestParams == null ? "{}" : requestParams);
        step.setResultJson("{}");
        step.setErrorCode("");
        step.setFailureReason("");
        step.setCreatedTime(now);
        step.setUpdatedTime(now);

        return step;
    }

}

