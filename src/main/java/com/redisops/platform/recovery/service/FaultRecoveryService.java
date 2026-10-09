package com.redisops.platform.recovery.service;

import com.redisops.platform.recovery.model.FaultRecoveryTaskRequest;
import com.redisops.platform.operation.model.OperationTaskDetail;

/**
 * 三节点 Redis + Sentinel 故障恢复业务接口。
 */
public interface FaultRecoveryService {

    /**
     * 校验故障恢复请求，生成完整拓扑恢复步骤并开始调度。
     *
     * @param request 故障恢复业务参数
     * @return 已创建的运维任务详情
     */
    OperationTaskDetail createAndDispatch(FaultRecoveryTaskRequest request);
}
