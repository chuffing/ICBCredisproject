package com.redisops.platform.serverswitch.service.impl;

import com.redisops.platform.operation.entity.OperationTask;
import com.redisops.platform.operation.service.OperationTaskCompletionHandler;
import com.redisops.platform.operation.type.BusinessTaskType;
import com.redisops.platform.redisinfo.service.RedisInfoService;
import org.springframework.stereotype.Component;

/**
 * 服务器切换全部节点验收成功后提交redis_info正式纳管关系。
 */
@Component
public class ServerSwitchCompletionHandler implements OperationTaskCompletionHandler {

    private final RedisInfoService redisInfoService;

    /** 注入正式Redis拓扑服务。 */
    public ServerSwitchCompletionHandler(RedisInfoService redisInfoService) {
        this.redisInfoService = redisInfoService;
    }

    /** 只处理服务器切换业务任务。 */
    @Override
    public boolean supports(String taskType) {
        return BusinessTaskType.SERVER_SWITCH.name().equals(taskType);
    }

    /** 把任务创建时保存的目标拓扑原子提交到redis_info。 */
    @Override
    public void handleSuccess(OperationTask task) {
        redisInfoService.commitSuccessfulServerSwitch(task);
    }
}
