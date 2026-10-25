package com.danuvius.platform.service;

/**
 * 运维任务编号服务。
 */
public interface TaskNoService {

    /**
     * 生成下一个运维任务编号。
     *
     * @return 运维任务编号
     */
    String nextTaskNo();
}
