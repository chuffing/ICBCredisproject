package com.redisops.platform.operation.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * 运维任务步骤推进使用的延迟调度器。
 */
@Configuration
public class OperationSchedulingConfig {

    /**
     * 创建单线程步骤调度器，保证同一个平台实例按顺序推进任务。
     *
     * @return 运维步骤调度器
     */
    @Bean("operationTaskScheduler")
    public TaskScheduler operationTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("operation-scheduler-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(10);
        return scheduler;
    }
}
