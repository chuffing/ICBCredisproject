package com.danuvius.platform.service;

import com.danuvius.platform.model.dto.monitor.ExportFileDescriptor;
import com.danuvius.platform.model.dto.monitor.ReportExportResponse;
import com.danuvius.platform.model.dto.monitor.ReportQueryRequest;

/**
 * 报告导出任务编排，导出任务的创建、异步生成
 */
public interface ReportExportService {

    /**
     * 创建导出任务并异步生成文件
     */
    ReportExportResponse exportReport(ReportQueryRequest req);

    /**
     * 查询导出任务状态，前端3s轮询
     */
    ReportExportResponse queryExportTask(String taskId);

    /**
     * 获取导出文件，任务成功后下载
     */
    ExportFileDescriptor getExportFile(String taskId);
}
