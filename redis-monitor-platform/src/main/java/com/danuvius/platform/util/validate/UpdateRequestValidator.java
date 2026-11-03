package com.danuvius.platform.util.validate;

import com.danuvius.platform.common.constant.CommonConstant;
import com.danuvius.platform.common.exception.validator.IdValidator;
import com.danuvius.platform.common.exception.validator.ListValidator;
import com.danuvius.platform.common.exception.validator.NumberValidator;
import com.danuvius.platform.common.exception.validator.StringValidator;
import com.danuvius.platform.model.dto.alarm.rule.UpdateRequest;
import org.apache.commons.lang3.StringUtils;


public class UpdateRequestValidator {
    public static void validate(UpdateRequest req) {
        StringValidator.notBlank(req.getRuleId(), "ruleId");
        IdValidator.check(req.getRuleId(),"RL_",32,"ruleId");

        StringValidator.notBlank(req.getRuleName(), "ruleName");
        StringValidator.maxLength(req.getRuleName(), 64, "ruleName");

        StringValidator.isEnumValue(req.getMetricKey(),CommonConstant.RULE_METRIC_KEYS,"metricKey");
        // metricName 由后端按 metricKey 从枚举派生，客户端可不再传；传了也只做长度防御
        if (StringUtils.isNotBlank(req.getMetricName())) {
            StringValidator.maxLength(req.getMetricName(), 64, "metricName");
        }

        StringValidator.isEnumValue(req.getCompareType(), CommonConstant.COMPARE_TYPES, "compareType");

        NumberValidator.notNull(req.getThreshold(), "threshold");
        NumberValidator.nonNegative(req.getThreshold(), "threshold");
        NumberValidator.valueBetween(req.getThreshold(), 0, 1_000_000_000_000d, "threshold");

        StringValidator.notNull(req.getUnit(), "unit");
        if(StringUtils.isNotBlank(req.getUnit())){
            StringValidator.isEnumValue(req.getUnit(),CommonConstant.UNIT_TYPES, "unit");
        }

        StringValidator.notBlank(req.getCollectInterval(), "collectInterval");
        NumberValidator.parseInteger(req.getCollectInterval(),"collectInterval");

        StringValidator.notBlank(req.getDuration(), "duration");
        NumberValidator.parseInteger(req.getDuration(),"duration");

        StringValidator.isEnumValue(req.getAlarmLevel(), CommonConstant.ALARM_LEVELS, "alarmLevel");

        StringValidator.notBlank(req.getSilenceDuration(), "silenceDuration");
        NumberValidator.parseInteger(req.getSilenceDuration(),"silenceDuration");

        StringValidator.isEnumValue(req.getScopeType(), CommonConstant.SCOPE_TYPES, "scopeType");

        if ("2".equals(req.getScopeType())) {
            ListValidator.notEmpty(req.getScopeInstanceIds(), "scopeInstanceIds");
            ListValidator.maxSize(req.getScopeInstanceIds(), 100, "scopeInstanceIds");
            ListValidator.noBlankStringElement(req.getScopeInstanceIds(), "scopeInstanceIds");
        }else {
            ListValidator.noNullElement(req.getScopeInstanceIds(), "scopeInstanceIds");
        }

        StringValidator.isEnumValue(req.getEnableStatus(), CommonConstant.ENABLE_STATUS, "enableStatus");



    }
}
