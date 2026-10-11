package com.redisops.platform.operation.model;

import lombok.Data;
import com.redisops.platform.operation.entity.OperationTask;
import com.redisops.platform.operation.entity.OperationTaskStep;

import java.util.List;

/**
 * 任务主记录和步骤记录组成的查询结果。
 */
@Data
public class OperationTaskDetail {

    private final OperationTask task;
    private final List<OperationTaskStep> steps;
}
