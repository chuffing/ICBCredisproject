package com.danuvius.platform.util;

import com.danuvius.platform.common.constant.ReportConstants;
import com.danuvius.platform.model.enums.report.ExportTaskStatusEnum;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * 报告导出任务存储
 * 异步提交报告生成任务，同步返回任务id，前端3s轮询任务状态，
 * 任务与临时文件 30 分钟有效，过期自动清理
 */
@Component
public class ReportExportTaskHolder {

    /** 任务状态 */
    public static class ExportTask {
        private String taskId;
        /** pending / success / fail */
        private String taskStatus;
        private String downloadUrl;
        private String failReason;
        private String filePath;
        private String fileName;
        private LocalDateTime createTime;

        public ExportTask(String taskId) {
            this.taskId = taskId;
            this.taskStatus = ExportTaskStatusEnum.PENDING.getCode();
            this.createTime = LocalDateTime.now();
        }

        public String getTaskId() { return taskId; }
        public String getTaskStatus() { return taskStatus; }
        public void setTaskStatus(String taskStatus) { this.taskStatus = taskStatus; }
        public String getDownloadUrl() { return downloadUrl; }
        public void setDownloadUrl(String downloadUrl) { this.downloadUrl = downloadUrl; }
        public String getFailReason() { return failReason; }
        public void setFailReason(String failReason) { this.failReason = failReason; }
        public String getFilePath() { return filePath; }
        public void setFilePath(String filePath) { this.filePath = filePath; }
        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }
        public LocalDateTime getCreateTime() { return createTime; }
    }

    private final Cache<String, ExportTask> taskCache;

    /** 文件目录：默认系统临时目录下的 redis-report-export */
    @Value("${platform.report.export.dir:}")
    private String exportDirConfig;

    public ReportExportTaskHolder() {
        this.taskCache = Caffeine.newBuilder()
                .expireAfterWrite(ReportConstants.EXPORT_TASK_TTL_MINUTES, TimeUnit.MINUTES)
                .maximumSize(ReportConstants.EXPORT_TASK_CACHE_MAX)
                .removalListener((String key, ExportTask task, com.github.benmanes.caffeine.cache.RemovalCause cause) -> {
                    // 任务过期时清理临时文件
                    if (task != null && task.getFilePath() != null) {
                        File file = new File(task.getFilePath());
                        if (file.exists() && !file.delete()) {
                            file.deleteOnExit();
                        }
                    }
                })
                .build();
    }

    /** 创建任务 */
    public ExportTask createTask(String taskId) {
        ExportTask task = new ExportTask(taskId);
        taskCache.put(taskId, task);
        return task;
    }

    /** 查询任务 */
    public ExportTask getTask(String taskId) {
        return taskCache.getIfPresent(taskId);
    }

    /** 导出文件目录 */
    public String exportDir() {
        if (exportDirConfig != null && !exportDirConfig.trim().isEmpty()) {
            return exportDirConfig;
        }
        return System.getProperty("java.io.tmpdir") + File.separator + "redis-report-export";
    }
}
