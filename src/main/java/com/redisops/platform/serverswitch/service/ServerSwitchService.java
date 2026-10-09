package com.redisops.platform.serverswitch.service;

import com.redisops.platform.operation.model.OperationTaskDetail;
import com.redisops.platform.serverswitch.model.ServerSwitchTaskRequest;

/**
 * 三节点服务器切换业务编排接口。
 */
public interface ServerSwitchService {

    /** 校验目标节点并创建、下发完整服务器切换任务。 */
    OperationTaskDetail createAndDispatch(ServerSwitchTaskRequest request);
}
