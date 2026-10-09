package com.redisops.platform.operation.model;

import com.redisops.platform.operation.entity.OperationTask;
import com.redisops.platform.operation.entity.OperationTaskStep;

import java.util.List;

/**
 * 任务主记录和步骤记录组成的查询结果。
 */
public class OperationTaskDetail {

    private final OperationTask task;
    private final List<OperationTaskStep> steps;

    /** 创建任务详情。 */
    public OperationTaskDetail(OperationTask task, List<OperationTaskStep> steps) {
        this.task = task;
        this.steps = steps;
    }

    /** 获取任务主记录。 */ public OperationTask getTask() { return task; }
    /** 获取任务步骤。 */ public List<OperationTaskStep> getSteps() { return steps; }
}
