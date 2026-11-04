package com.danuvius.platform.service;

import com.danuvius.platform.model.dto.monitor.ReportQueryRequest;
import com.danuvius.platform.model.dto.monitor.ReportQueryResponse;

/**
 * 日周报数据查询与组装
 */
public interface ReportQueryService {

    /**
     * 日周报查询（带缓存）
     */
    ReportQueryResponse queryReport(ReportQueryRequest req);

    /**
     * 组装完整报告数据，查询与导出共用
     * @param instanceId 实例id
     * @param reportType 报告类型：日报/周报
     */
    ReportQueryResponse buildReportData(String instanceId, String reportType);
}
