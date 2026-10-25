package com.danuvius.platform.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 高风险运维任务线程池配置。
 */
@Configuration
public class OperationTaskExecutorConfig {

    /**
     * 创建高风险运维任务专用线程池。
     * 同一时间只允许存在一个高风险运维任务，因此使用单线程顺序调度。
     *
     * @return 高风险运维任务线程池
     */
    @Bean("operationTaskExecutor")
    public Executor operationTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(16);
        executor.setThreadNamePrefix("operation-task-");
        executor.initialize();
        return executor;
    }

    /**
     * 创建同阶段执行器任务并行下发线程池。
     * 一个故障恢复阶段包含三台执行器，因此固定使用三个工作线程。
     *
     * @return 同阶段任务下发线程池
     */
    @Bean("operationStepExecutor")
    public Executor operationStepExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(3);
        executor.setMaxPoolSize(3);
        executor.setQueueCapacity(16);
        executor.setThreadNamePrefix("operation-step-");
        executor.initialize();
        return executor;
    }
}
