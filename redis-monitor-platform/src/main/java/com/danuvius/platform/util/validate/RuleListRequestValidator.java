package com.danuvius.platform.util.validate;

import com.danuvius.platform.common.constant.CommonConstant;
import com.danuvius.platform.common.exception.validator.ListValidator;
import com.danuvius.platform.common.exception.validator.NumberValidator;
import com.danuvius.platform.common.exception.validator.StringValidator;
import com.danuvius.platform.model.dto.alarm.rule.RuleListRequest;
import org.apache.commons.lang3.StringUtils;

public class RuleListRequestValidator {
    public static void validate(RuleListRequest req) {
        NumberValidator.valueBetween(req.getPageNum(), 1, 100, "pageNum");
        NumberValidator.valueBetween(req.getPageSize(), 1, 20, "pageSize");

        //alarmLevel、enableStatus 可选，未传时不做非空
        if (StringUtils.isNotBlank(req.getAlarmLevel())) {
            StringValidator.isEnumValue(req.getAlarmLevel(), CommonConstant.ALARM_LEVELS, "alarmLevel");
        }

        if (StringUtils.isNotBlank(req.getEnableStatus())) {
            StringValidator.isEnumValue(req.getEnableStatus(), CommonConstant.ENABLE_STATUS, "enableStatus");
        }

        ListValidator.noNullElement(req.getScopeInstanceIds(), "scopeInstanceIds");
        ListValidator.maxSize(req.getScopeInstanceIds(), 100, "scopeInstanceIds");


        if (StringUtils.isNotBlank(req.getKeyword())) {
            StringValidator.maxLength(req.getKeyword(), 64, "keyword");
        }

    }
}
