package com.danuvius.platform.service.impl;

import com.danuvius.platform.model.dto.TaskResultReportDTO;
import com.danuvius.platform.service.OperationTaskDispatchExecutor;
import com.danuvius.platform.service.OperationTaskResultService;
import com.danuvius.platform.service.OperationTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 运维任务执行结果处理服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OperationTaskResultServiceImpl
        implements OperationTaskResultService {

    private final OperationTaskService operationTaskService;

    private final OperationTaskDispatchExecutor operationTaskDispatchExecutor;

    /**
     * 接收执行器结果，先提交步骤事实，再独立汇总当前阶段。
     */
    @Override
    public void reportTaskResult(TaskResultReportDTO request) {

        // ======== 1. 独立提交执行器步骤结果 ========

        operationTaskService.saveStepResult(request);

        // ======== 2. 独立汇总当前阶段结果 ========

        finishStageSafely(request.getTaskNo(), request.getStepNo());
    }

    /**
     * 将长时间未上报结果的步骤标记为结果未知。
     */
    @Override
    public boolean markResultUnknown(
            String taskNo,
            Integer stepNo,
            String executorId,
            String reason) {

        // ======== 1. 独立提交步骤结果未知状态 ========

        boolean updated = operationTaskService.markStepResultUnknown(
                taskNo,
                stepNo,
                executorId,
                reason
        );
        if (!updated) {
            return false;
        }

        // ======== 2. 独立汇总当前阶段结果 ========

        finishStageSafely(taskNo, stepNo);
        return true;
    }

    /**
     * 汇总阶段失败时保留已提交的步骤结果，并将总任务标记为结果未知。
     */
    private void finishStageSafely(String taskNo, Integer stepNo) {

        try {
            // 汇总当前阶段。返回下一阶段编号；无需继续下发时返回null。
            Integer nextStepNo = operationTaskService.finishStage(
                    taskNo,
                    stepNo
            );

            // finishStage返回时当前事务已经提交，可以安全下发下一阶段。
            if (nextStepNo != null) {
                operationTaskDispatchExecutor.dispatchStage(
                        taskNo,
                        nextStepNo
                );
            }

        } catch (RuntimeException finishException) {
            log.error(
                    "运维任务阶段结果汇总失败，taskNo={}，stepNo={}",
                    taskNo,
                    stepNo,
                    finishException
            );

            String reason = "阶段"
                    + stepNo
                    + "结果汇总失败，请人工确认";

            try {
                // 当前没有外层事务，本次状态修改使用独立事务提交。
                operationTaskService.markTaskResultUnknown(
                        taskNo,
                        reason
                );

            } catch (RuntimeException updateException) {
                // 连结果未知也无法保存时，将异常继续返回并保留原始原因。
                finishException.addSuppressed(updateException);
                throw finishException;
            }
        }
    }
}
