package com.redisops.platform.agent.task;

import com.redisops.platform.agent.service.AgentRegistrationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定时检查执行器是否已经超过 60 秒没有发送心跳。
 */
@Component
public class ExecutorOfflineCheckTask {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExecutorOfflineCheckTask.class);

    private final AgentRegistrationService agentRegistrationService;

    /** 注入执行器注册服务。 */
    public ExecutorOfflineCheckTask(AgentRegistrationService agentRegistrationService) {
        this.agentRegistrationService = agentRegistrationService;
    }

    /** 每 30 秒执行一次离线状态检查。 */
    @Scheduled(fixedDelay = 30000L)
    public void markTimedOutExecutorsOffline() {
        int affectedRows = agentRegistrationService.markTimedOutExecutorsOffline();
        if (affectedRows > 0) {
            LOGGER.warn("本次心跳检查将 {} 个执行器标记为 OFFLINE", affectedRows);
        }
    }
}
