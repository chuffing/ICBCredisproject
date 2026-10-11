package com.redisops.platform.operation.task;

import lombok.RequiredArgsConstructor;
import com.redisops.platform.operation.service.OperationTaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 定时核对已下发但长时间没有收到回调的运维步骤。
 */
@Component
@RequiredArgsConstructor
public class OperationResultReconcileTask {

    private static final Logger log = LoggerFactory.getLogger(
            OperationResultReconcileTask.class);

    private final OperationTaskService operationTaskService;

    @Value("${platform.operation.result-query-after-seconds:10}")
    private long resultQueryAfterSeconds;

    @Value("${platform.operation.result-unknown-after-seconds:120}")
    private long resultUnknownAfterSeconds;

    /** 按固定间隔查询执行器本地结果，并处理长期无法确认的步骤。 */
    @Scheduled(fixedDelayString =
            "${platform.operation.result-reconcile-interval-millis:10000}")
    public void reconcile() {
        try {
            LocalDateTime now = LocalDateTime.now();
            operationTaskService.reconcileUnconfirmedResults(
                    now.minusSeconds(resultQueryAfterSeconds),
                    now.minusSeconds(resultUnknownAfterSeconds));
        } catch (Exception e) {
            log.error("核对未确认运维步骤失败", e);
        }
    }
}
