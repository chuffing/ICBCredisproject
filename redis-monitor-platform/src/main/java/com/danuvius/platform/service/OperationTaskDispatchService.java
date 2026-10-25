package com.danuvius.platform.service;

/**
 * 运维任务阶段调度服务。
 */
public interface OperationTaskDispatchService {

    /**
     * 向各执行器下发运维任务的指定阶段。
     *
     * @param taskNo 运维任务编号
     * @param stepNo 阶段编号
     */
    void dispatchStage(String taskNo, Integer stepNo);
}
