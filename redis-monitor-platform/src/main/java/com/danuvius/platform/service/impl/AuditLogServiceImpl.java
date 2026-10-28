package com.danuvius.platform.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.danuvius.platform.mapper.AuditLogMapper;
import com.danuvius.platform.model.dto.audit.AuditLogItemDTO;
import com.danuvius.platform.model.dto.audit.AuditLogPageRequest;
import com.danuvius.platform.model.dto.audit.AuditLogPageResponse;
import com.danuvius.platform.model.entity.AuditLog;
import com.danuvius.platform.service.AuditLogService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 操作审计日志
 */
@Slf4j
@Service
public class AuditLogServiceImpl extends ServiceImpl<AuditLogMapper, AuditLog>
        implements AuditLogService {

    /** 默认操作人（可由请求头覆盖） */
    private static final String DEFAULT_OPERATOR_ID = "system";
    private static final String DEFAULT_OPERATOR_NAME = "系统";

    private final AuditLogMapper auditLogMapper;

    public AuditLogServiceImpl(AuditLogMapper auditLogMapper) {
        this.auditLogMapper = auditLogMapper;
    }

    @Override
    public void record(String module, String operationType, String targetId, String content) {
        //Todo 结合统一认证号修改
        String operatorId = DEFAULT_OPERATOR_ID;
        String operatorName = DEFAULT_OPERATOR_NAME;

        try {
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                String headerId = request.getHeader("X-Operator-Id");
                String headerName = request.getHeader("X-Operator-Name");
                if (headerId != null && !headerId.trim().isEmpty()) {
                    operatorId = headerId;
                }
                if (headerName != null && !headerName.trim().isEmpty()) {
                    operatorName = headerName;
                }
            }
        } catch (Exception e) {
            log.warn("[操作审计] 获取操作人信息失败，使用默认值", e);
        }
        record(operatorId, operatorName, module, operationType, targetId, content, "成功");
    }


    /*插表*/
    @Override
    public void record(String operatorId, String operatorName, String module, String operationType,
                       String targetId, String content, String operationResult) {
        try {
            LocalDateTime now = LocalDateTime.now();
            AuditLog auditLog = new AuditLog();

            auditLog.setOperatorId(operatorId);
            auditLog.setOperatorName(operatorName);
            auditLog.setModule(module);
            auditLog.setOperationType(operationType);
            auditLog.setTargetId(targetId);
            auditLog.setOperationContent(content);
            auditLog.setOperationResult(operationResult);
            auditLog.setOperationTime(now);
            auditLog.setCreatedTime(now);
            auditLogMapper.insert(auditLog);
        } catch (Exception e) {
            // 审计记录失败不能影响主业务流程
            log.error("[操作审计] 记录失败, module={}, operationType={}", module, operationType, e);
        }
    }

    /**
     * 拉审计表记录
     * @param req
     * @return
     */
    @Override
    public AuditLogPageResponse getAuditLogPage(AuditLogPageRequest req) {
        int pageNum = req.getPageNum();
        int pageSize = req.getPageSize();


        Page<AuditLog> page = page(new Page<>(pageNum, pageSize), Wrappers.<AuditLog>lambdaQuery()
                .eq(StringUtils.isNotBlank(req.getModule()),AuditLog::getModule, req.getModule())
                .eq(StringUtils.isNotBlank(req.getOperationType()),AuditLog::getOperationType, req.getOperationType())
                .and(StringUtils.isNotBlank(req.getKeyword()), i->
                        i.like(AuditLog::getOperationContent, req.getKeyword())
                                .or()
                                .like(AuditLog::getOperatorName, req.getKeyword())
                )
                .ge(req.getStartTime()!=null,AuditLog::getOperationTime,req.getStartTime())
                .le(req.getEndTime()!=null,AuditLog::getOperationTime,req.getEndTime())
                .orderByDesc(AuditLog::getOperationTime)
        );


        AuditLogPageResponse resp = new AuditLogPageResponse();
        resp.setPage(page);
        return resp;
    }
}
