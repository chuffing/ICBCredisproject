package com.danuvius.platform.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.danuvius.platform.common.constant.CommonConstant;
import com.danuvius.platform.common.constant.ReportConstants;
import com.danuvius.platform.common.exception.BusinessCode;
import com.danuvius.platform.common.exception.BusinessException;
import com.danuvius.platform.mapper.RedisInstanceMapper;
import com.danuvius.platform.model.dto.monitor.ExportFileDescriptor;
import com.danuvius.platform.model.dto.monitor.ReportExportResponse;
import com.danuvius.platform.model.dto.monitor.ReportQueryRequest;
import com.danuvius.platform.model.dto.monitor.ReportQueryResponse;
import com.danuvius.platform.model.entity.RedisInstance;
import com.danuvius.platform.model.enums.report.ExportTaskStatusEnum;
import com.danuvius.platform.executor.AsyncTaskRunner;
import com.danuvius.platform.service.AuditLogService;
import com.danuvius.platform.service.ReportExportService;
import com.danuvius.platform.service.ReportQueryService;
import com.danuvius.platform.util.ReportExportTaskHolder;
import com.danuvius.platform.util.ReportFileGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;

/**
 * 报告导出任务编排实现。
 */
@Slf4j
@Service
public class ReportExportServiceImpl implements ReportExportService {

    private final ReportQueryService reportQueryService;
    private final RedisInstanceMapper redisInstanceMapper;
    /* 报告导出异步任务存储 */
    private final ReportExportTaskHolder taskHolder;
    /* 报告导出文件生成器（异步任务内使用） */
    private final ReportFileGenerator reportFileGenerator;
    /* 通用异步任务执行器（独立Bean保证@Async生效） */
    private final AsyncTaskRunner asyncTaskRunner;
    /* 报告导出记操作审计日志 */
    private final AuditLogService auditLogService;

    public ReportExportServiceImpl(ReportQueryService reportQueryService,
                                   RedisInstanceMapper redisInstanceMapper,
                                   ReportExportTaskHolder taskHolder,
                                   ReportFileGenerator reportFileGenerator,
                                   AsyncTaskRunner asyncTaskRunner,
                                   AuditLogService auditLogService) {
        this.reportQueryService = reportQueryService;
        this.redisInstanceMapper = redisInstanceMapper;
        this.taskHolder = taskHolder;
        this.reportFileGenerator = reportFileGenerator;
        this.asyncTaskRunner = asyncTaskRunner;
        this.auditLogService = auditLogService;
    }

    /**
     * 报告导出
     * 异步生成文件并更新任务状态，任务与文件30分钟有效
     */
    @Override
    public ReportExportResponse exportReport(ReportQueryRequest req) {
        String instanceId = req.getInstanceId();
        String reportType = req.getReportType();
        String taskId = ReportConstants.EXPORT_TASK_ID_PREFIX + IdUtil.getSnowflakeNextIdStr();
        ReportExportTaskHolder.ExportTask task = taskHolder.createTask(taskId);

        asyncTaskRunner.run(() -> doExport(taskId, instanceId, reportType));

        ReportExportResponse resp = new ReportExportResponse();
        resp.setTaskId(taskId);
        resp.setTaskStatus(task.getTaskStatus());
        return resp;
    }

    /**
     * 报告导出异步任务执行体：生成文件并更新任务状态
     */
    private void doExport(String taskId, String instanceId, String reportType) {
        ReportExportTaskHolder.ExportTask task = taskHolder.getTask(taskId);
        if (task == null) {
            return;
        }
        try {
            log.info("[报告导出] 异步任务开始, taskId={}, instanceId={}, reportType={}",
                    taskId, instanceId, reportType);

            // 复用报告查询逻辑获取完整报告数据
            ReportQueryResponse report = reportQueryService.buildReportData(instanceId, reportType);
            String instanceName = getInstanceName(instanceId);
            File file = reportFileGenerator.generate(report, instanceId, reportType,
                    taskHolder.exportDir());

            task.setFilePath(file.getAbsolutePath());
            task.setFileName(file.getName());
            task.setDownloadUrl(ReportConstants.EXPORT_DOWNLOAD_URL_TEMPLATE + taskId);
            task.setTaskStatus(ExportTaskStatusEnum.SUCCESS.getCode());

            /* 将操作人、文件id、报告类型、导出实例、时间范围、操作类型、操作时间记入操作审计日志 */
            auditLogService.record(CommonConstant.MODULE_REPORT, CommonConstant.OPERATION_TYPE_EXPORT, taskId,
                    "导出Redis运行报告，实例：" + instanceId + "|" + instanceName + "，报告类型：" + reportType);

            log.info("[报告导出] 异步任务完成, taskId={}, file={}", taskId, file.getAbsolutePath());
        } catch (Exception e) {
            task.setTaskStatus(ExportTaskStatusEnum.FAIL.getCode());
            task.setFailReason(e.getMessage());
            log.error("[报告导出] 异步任务失败, taskId={}", taskId, e);
        }
    }

    /**
     * 查询导出任务状态
     */
    @Override
    public ReportExportResponse queryExportTask(String taskId) {
        ReportExportTaskHolder.ExportTask task = taskHolder.getTask(taskId);
        if (task == null) {
            throw new BusinessException(BusinessCode.NOT_FOUND_ERROR.getCode(), "导出任务不存在或已过期");
        }
        ReportExportResponse resp = new ReportExportResponse();
        resp.setTaskId(task.getTaskId());
        resp.setTaskStatus(task.getTaskStatus());
        resp.setDownloadUrl(task.getDownloadUrl());
        resp.setFailReason(task.getFailReason());
        return resp;
    }

    /**
     * 获取导出文件
     */
    @Override
    public ExportFileDescriptor getExportFile(String taskId) {
        ReportExportTaskHolder.ExportTask task = taskHolder.getTask(taskId);
        if (task == null) {
            throw new BusinessException(BusinessCode.NOT_FOUND_ERROR.getCode(), "导出任务不存在或已过期");
        }
        if (!ExportTaskStatusEnum.isSuccess(task.getTaskStatus()) || task.getFilePath() == null) {
            throw new BusinessException(BusinessCode.OPERATION_ERROR.getCode(), "导出任务未完成，无法下载");
        }
        File file = new File(task.getFilePath());
        if (!file.exists()) {
            throw new BusinessException(BusinessCode.OPERATION_ERROR.getCode(), "导出文件已过期，请重新导出");
        }
        String fileName = task.getFileName() == null ? file.getName() : task.getFileName();
        return new ExportFileDescriptor(file, fileName, ReportConstants.MIME_PDF);
    }

    /** 取实例名称，仅用于审计日志描述 */
    private String getInstanceName(String instanceId) {
        RedisInstance instance = redisInstanceMapper.selectOne(Wrappers.<RedisInstance>lambdaQuery()
                .eq(RedisInstance::getInstanceId, instanceId));
        return instance == null ? null : instance.getInstanceName();
    }
}
