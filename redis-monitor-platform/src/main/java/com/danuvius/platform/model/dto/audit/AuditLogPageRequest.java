package com.danuvius.platform.model.dto.audit;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 操作审计分页查询请求 */
@Data
public class AuditLogPageRequest implements Serializable {

    /** 功能模块筛选 */
    private String module;

    /** 操作类型筛选 */
    private String operationType;

    /** 关键字，模糊匹配操作内容/操作人 */
    private String keyword;

    /** 开始时间，格式 yyyy-MM-dd HH:mm:ss */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    /** 结束时间，格式 yyyy-MM-dd HH:mm:ss */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

    /** 页码，默认 1 */
    private Integer pageNum = 1;

    /** 每页条数，默认 12 */
    private Integer pageSize = 12;
}
