package com.danuvius.platform.util;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 告警检测任务状态记录器
 * 告警监控页概览返回"告警检测任务状态（运行正常/运行异常）"，
 * 读取最近1次检测任务的执行时间与执行结果
 * 手动检测（单规则/全量）与后续自动检测任务完成后调用 record 记录
 */
@Component
public class DetectTaskRecorder {

    /** 任务类型 -> 最近一次执行结果 */
    private final Map<String, TaskRecord> records = new ConcurrentHashMap<>();

    /**
     * 记录一次检测任务执行结果
     *
     * @param taskType 任务类型，如 single-rule / all-rule
     * @param success  是否执行成功
     */
    public void record(String taskType, boolean success) {
        records.put(taskType, new TaskRecord(LocalDateTime.now(), success));
    }

    /**
     * 获取最近一次检测任务状态
     * 优先取全部规则检测，无则取单规则检测；均无记录时返回"运行异常"
     */
    public String currentStatus() {
        TaskRecord record = records.get("all-rule");
        if (record == null) {
            record = records.get("single-rule");
        }
        if (record == null) {
            return "运行异常";
        }
        return record.success ? "运行正常" : "运行异常";
    }


    private static class TaskRecord {
        private final LocalDateTime executeTime;
        private final boolean success;

        private TaskRecord(LocalDateTime executeTime, boolean success) {
            this.executeTime = executeTime;
            this.success = success;
        }
    }
}
