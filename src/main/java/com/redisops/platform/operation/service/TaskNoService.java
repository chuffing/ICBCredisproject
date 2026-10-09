package com.redisops.platform.operation.service;

/**
 * 业务任务编号生成接口。
 */
public interface TaskNoService {

    /** 生成当天的下一个业务任务编号。 */
    String nextTaskNo();
}
