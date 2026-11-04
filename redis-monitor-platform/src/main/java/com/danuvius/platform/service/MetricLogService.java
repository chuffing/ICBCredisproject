package com.danuvius.platform.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.danuvius.platform.model.dto.metric.MetricReportItemDTO;
import com.danuvius.platform.model.entity.MetricLog;

public interface MetricLogService extends IService<MetricLog> {
    /*上报指标：写 rms_metric_log+ 主从切换检测*/
    void reportMetric(MetricReportItemDTO metric);
    /*查询某实例最新一条指标*/
    MetricLog getLatestByInstanceId(String agentId);
}