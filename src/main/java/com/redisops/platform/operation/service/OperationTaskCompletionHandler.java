package com.redisops.platform.operation.service;

import com.redisops.platform.operation.entity.OperationTask;

/**
 * 在运维任务准备标记成功时提交对应业务的数据库结果。
 */
public interface OperationTaskCompletionHandler {

    /** 判断当前处理器是否负责指定业务任务类型。 */
    boolean supports(String taskType);

    /** 在任务成功事务中提交业务结果；抛出异常会让整个成功事务回滚。 */
    void handleSuccess(OperationTask task);
}
