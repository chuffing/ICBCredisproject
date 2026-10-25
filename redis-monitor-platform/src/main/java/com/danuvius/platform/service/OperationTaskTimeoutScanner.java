package com.danuvius.platform.service;

import com.danuvius.platform.config.OperationTaskProperties;
import com.danuvius.platform.mapper.OperationTaskStepMapper;
import com.danuvius.platform.model.entity.OperationTaskStep;
import com.danuvius.platform.model.enums.OperationTaskStatusEnum;
import com.danuvius.platform.model.enums.OperationTaskStepStatusEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * 运维任务执行结果超时扫描器。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OperationTaskTimeoutScanner {

    private final OperationTaskStepMapper operationTaskStepMapper;

    private final OperationTaskResultService operationTaskResultService;

    private final OperationTaskProperties operationTaskProperties;

    /**
     * 扫描已经下发但长时间没有收到执行结果的步骤。
     */
    @Scheduled(fixedDelayString = "${platform.operation-task.timeout-scan-interval-millis:5000}")
    public void scanResultTimeout() {

        // ======== 1. 查询运行中任务的等待结果步骤 ========

        // 没有运行中的运维任务时，查询结果为空，本轮扫描会立即结束。
        List<OperationTaskStep> waitingSteps =
                operationTaskStepMapper.selectWaitingResultSteps(
                        OperationTaskStepStatusEnum.DISPATCHED.name(),
                        Collections.singletonList(
                                OperationTaskStatusEnum.RUNNING.name()
                        )
                );
        if (waitingSteps.isEmpty()) {
            return;
        }

        // ======== 2. 按照操作类型判断结果等待时间 ========

        LocalDateTime now = LocalDateTime.now();
        for (OperationTaskStep step : waitingSteps) {
            markResultUnknownIfTimeout(step, now);
        }
    }

    /**
     * 当前步骤超过结果等待时间后，将步骤修改为RESULT_UNKNOWN。
     */
    private void markResultUnknownIfTimeout(
            OperationTaskStep step,
            LocalDateTime now) {

        LocalDateTime waitingSince = step.getUpdatedTime();

        long timeoutSeconds = operationTaskProperties
                .resolveResultTimeoutSeconds(step.getOperationType());

        if (waitingSince.plusSeconds(timeoutSeconds).isAfter(now)) {
            return;
        }

        // 先组装好reason。
        String reason = "等待执行器上报"
                + step.getOperationType()
                + "执行结果超过"
                + timeoutSeconds
                + "秒，结果暂时无法确认";

        try {
            boolean updated = operationTaskResultService.markResultUnknown(
                    step.getTaskNo(),
                    step.getStepNo(),
                    step.getExecutorId(),
                    reason
            );

            // 执行器结果可能刚好先到达，条件更新未生效时无需记录超时日志。
            if (!updated) {
                return;
            }

            log.warn("运维任务步骤等待执行结果超时，"
                            + "taskNo={}, stepNo={}, executorId={}, "
                            + "operationType={}, timeoutSeconds={}",
                    step.getTaskNo(),
                    step.getStepNo(),
                    step.getExecutorId(),
                    step.getOperationType(),
                    timeoutSeconds);

        } catch (Exception e) {
            // 单个步骤处理失败不能影响其他步骤的超时检查。
            log.error("运维任务步骤超时状态保存失败，"
                            + "taskNo={}, stepNo={}, executorId={}",
                    step.getTaskNo(),
                    step.getStepNo(),
                    step.getExecutorId(),
                    e);
        }
    }
}
