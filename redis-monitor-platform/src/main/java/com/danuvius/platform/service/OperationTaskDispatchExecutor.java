package com.danuvius.platform.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 运维任务阶段异步调度入口。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OperationTaskDispatchExecutor {

    private final OperationTaskDispatchService operationTaskDispatchService;

    /**
     * 使用运维任务线程池异步下发指定阶段。
     */
    @Async("operationTaskExecutor")
    public void dispatchStage(String taskNo, Integer stepNo) {

        try {
            // 调用阶段调度业务。
            operationTaskDispatchService.dispatchStage(taskNo, stepNo);

        } catch (Exception e) {
            // 异步线程无法将异常返回给Controller，只记录完整日志供人工排查。
            log.error(
                    "运维任务阶段调度异常，taskNo={}，stepNo={}",
                    taskNo,
                    stepNo,
                    e
            );
        }
    }
}
