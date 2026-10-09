package com.redisops.platform.agent.status.service;

import com.redisops.platform.agent.status.entity.ExecutorRuntimeStatus;
import com.redisops.platform.agent.status.model.MetricReportRequest;

import java.util.List;

/** 接收并查询执行器上报的Redis和Sentinel最新状态。 */
public interface ExecutorRuntimeStatusService {

    /** 鉴权后保存一台执行器的最新状态。 */
    void receive(String authorization, MetricReportRequest request);

    /** 查询所有执行器的最新状态。 */
    List<ExecutorRuntimeStatus> listLatest();
}