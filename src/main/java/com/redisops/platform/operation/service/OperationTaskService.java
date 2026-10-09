package com.redisops.platform.operation.service;

import com.redisops.platform.operation.model.CreateOperationTaskRequest;
import com.redisops.platform.operation.model.OperationTaskDetail;
import com.redisops.platform.operation.model.TaskResultReportRequest;

/**
 * 运维任务创建、调度、结果汇总和查询接口。
 */
public interface OperationTaskService {

    /** 创建任务和步骤并下发第一步骤组。 */
    OperationTaskDetail createAndDispatch(CreateOperationTaskRequest request);

    /** 接收并处理执行器上报的步骤结果。 */
    void receiveResult(String authorization, TaskResultReportRequest request);

    /** 查询任务主记录和全部步骤。 */
    OperationTaskDetail getTaskDetail(String taskNo);
}
