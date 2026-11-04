package com.danuvius.platform.executor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AsyncTaskRunner {

    @Async("taskExecutor")
    public void run(Runnable task) {
        try {
            task.run();
        } catch (Exception e) {
            log.error("[异步任务] 执行失败", e);
        }
    }
}