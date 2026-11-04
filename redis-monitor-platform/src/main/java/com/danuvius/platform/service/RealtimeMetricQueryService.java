package com.danuvius.platform.service;

import com.danuvius.platform.model.dto.monitor.MetricCurrentValueResponse;
import com.danuvius.platform.model.dto.monitor.RedisInstanceItemDTO;
import com.danuvius.platform.model.dto.monitor.TrendRequest;
import com.danuvius.platform.model.dto.monitor.TrendResponse;

import java.util.List;

/**
 * 实时监控查询，实例列表、核心实时指标、多节点趋势
 */
public interface RealtimeMetricQueryService {

    /**
     * 查实例列表
     * @param managementStatus 纳管状态
     */
    List<RedisInstanceItemDTO> getInstanceList(String managementStatus);

    /**
     * 拉核心实时指标
     * @param instanceId 实例id
     */
    MetricCurrentValueResponse getCurrentValues(String instanceId);

    /**
     * 多节点指标趋势
     * @param req 实例列表 + 时间范围
     */
    TrendResponse getTrend(TrendRequest req);
}
