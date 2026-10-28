package com.danuvius.platform.model.dto.alarm.record;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/**
 * 告警记录分页查询请求
 */
@Data
public class RecordPageRequest {

    /** 实例ID（可选） */
    private String instanceId;

    /** 告警级别（可选） */
    private String alarmLevel;

    /** 指标 key（可选） */
    private String metricKey;

    /** 恢复状态（可选） */
    private String recoverStatus;

    /** 关键字，模糊匹配告警编号/内容（可选） */
    private String keyword;

    /** 开始时间（可选），格式 yyyy-MM-dd HH:mm:ss */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    /** 结束时间（可选），格式 yyyy-MM-dd HH:mm:ss */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

    /** 页码，默认 1 */
    private Integer pageNum = 1;

    /** 每页条数，默认 12 */
    private Integer pageSize = 12;
}