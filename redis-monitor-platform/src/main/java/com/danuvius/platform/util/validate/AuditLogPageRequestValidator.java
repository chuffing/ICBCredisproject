package com.danuvius.platform.util.validate;

import com.danuvius.platform.common.constant.CommonConstant;
import com.danuvius.platform.common.exception.validator.DateValidator;
import com.danuvius.platform.common.exception.validator.NumberValidator;
import com.danuvius.platform.common.exception.validator.StringValidator;
import com.danuvius.platform.model.dto.audit.AuditLogPageRequest;
import org.apache.commons.lang3.StringUtils;

public class AuditLogPageRequestValidator {
    public static void validate(AuditLogPageRequest req){
        /*所有字段均非必输*/
        NumberValidator.valueBetween(req.getPageNum(), 1, 100, "pageNum");
        NumberValidator.valueBetween(req.getPageSize(), 1, 20, "pageSize");
        if (StringUtils.isNotBlank(req.getModule())) {
            StringValidator.maxLength(req.getModule(), 32, "module");
            StringValidator.isEnumValue(req.getModule(), CommonConstant.AUDIT_MODULE_KEYS,"module");

        }

        if (StringUtils.isNotBlank(req.getOperationType())) {
            StringValidator.maxLength(req.getOperationType(), 32, "operationType");
            StringValidator.isEnumValue(req.getOperationType(), CommonConstant.AUDIT_OPERATION_TYPES,"module");

        }

        // keyword 为可选模糊查询条件，未传时不做非空校验
        StringValidator.maxLength(req.getKeyword(), 128, "keyword");

        if(req.getStartTime()!=null&&req.getEndTime()!=null) {
            DateValidator.range(req.getStartTime(), req.getEndTime(), "startTime", "endTime");
        }
    }
}
