package com.redisops.platform.operation.service;

import com.redisops.platform.operation.entity.OperationTask;
import com.redisops.platform.operation.entity.OperationTaskStep;
import com.redisops.platform.operation.model.CreateOperationTaskStepRequest;

import java.util.List;

/**
 * 为需要平台级补偿的业务任务生成回滚步骤。
 */
public interface OperationTaskRollbackPlanner {

    /** 判断当前规划器是否负责指定业务任务。 */
    boolean supports(OperationTask task);

    /**
     * 根据失败时已经完成的步骤生成补偿计划；空列表表示该阶段无需补偿。
     */
    List<CreateOperationTaskStepRequest> buildRollbackSteps(
            OperationTask task, List<OperationTaskStep> currentSteps);
}
