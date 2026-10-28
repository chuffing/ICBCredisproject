package com.danuvius.platform.controller;

import com.danuvius.platform.common.core.ResultBean;
import com.danuvius.platform.common.exception.ErrorCode;
import com.danuvius.platform.model.dto.audit.AuditLogPageRequest;
import com.danuvius.platform.model.dto.audit.AuditLogPageResponse;
import com.danuvius.platform.service.AuditLogService;
import com.danuvius.platform.util.validate.AuditLogPageRequestValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 查询操作审计列表接口
 */
@Slf4j
@RestController
@RequestMapping({"/audit"})
public class AuditController {

    private final AuditLogService auditLogService;

    public AuditController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    /**
     * 查询操作审计列表接口。
     * 仪表盘展示最近5条操作记录时传 pageSize=5。
     */
    @GetMapping("/logs")
    public ResultBean<AuditLogPageResponse> logs( AuditLogPageRequest req) {
        AuditLogPageRequestValidator.validate(req);
        log.info("查询操作审计列表接口：{}", req.toString());
        return ResultBean.restResult(auditLogService.getAuditLogPage(req), ErrorCode.OK);
    }
}
