package com.danuvius.platform.service;

import com.danuvius.platform.model.dto.TaskResultReportDTO;

/**
 * 运维任务执行结果处理服务。
 */
public interface OperationTaskResultService {

    /**
     * 处理执行器上报的任务执行结果。
     *
     * @param request 任务执行结果
     */
    void reportTaskResult(TaskResultReportDTO request);

    /**
     * 将长期没有收到执行结果的步骤标记为结果未知。
     *
     * @param taskNo 运维任务编号
     * @param stepNo 阶段编号
     * @param executorId 执行器编号
     * @param reason 结果未知原因
     * @return true表示本次已将步骤修改为结果未知
     */
    boolean markResultUnknown(
            String taskNo,
            Integer stepNo,
            String executorId,
            String reason);
}
