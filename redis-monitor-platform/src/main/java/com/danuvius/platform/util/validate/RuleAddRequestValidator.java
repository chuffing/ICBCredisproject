package com.danuvius.platform.util.validate;

import com.danuvius.platform.common.constant.CommonConstant;
import com.danuvius.platform.common.exception.validator.ListValidator;
import com.danuvius.platform.common.exception.validator.NumberValidator;
import com.danuvius.platform.common.exception.validator.StringValidator;
import com.danuvius.platform.model.dto.alarm.rule.RuleAddRequest;
import org.apache.commons.lang3.StringUtils;



public class RuleAddRequestValidator {

    public static void validate(RuleAddRequest req)
    {
        StringValidator.notBlank(req.getRuleName(), "ruleName");
        StringValidator.maxLength(req.getRuleName(), 64, "ruleName");

        StringValidator.notBlank(req.getMetricKey(), "metricKey");
        StringValidator.isEnumValue(req.getMetricKey(), CommonConstant.RULE_METRIC_KEYS, "metricKey");
        StringValidator.isEnumValue(req.getCompareType(), CommonConstant.COMPARE_TYPES, "compareType");

        NumberValidator.notNull(req.getThreshold(), "threshold");
        NumberValidator.nonNegative(req.getThreshold(), "threshold");

        NumberValidator.valueBetween(req.getThreshold(), 0, 1_000_000_000_000d, "threshold");
        StringValidator.notNull(req.getUnit(), "unit");
        //如果非空串
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
        // scopeType=2 时必须指定生效实例
        if ("2".equals(req.getScopeType())) {
            ListValidator.notEmpty(req.getScopeInstanceIds(), "scopeInstanceIds");
            ListValidator.maxSize(req.getScopeInstanceIds(), 100, "scopeInstanceIds");
            ListValidator.noBlankStringElement(req.getScopeInstanceIds(), "scopeInstanceIds");
        }else {
            ListValidator.noNullElement(req.getScopeInstanceIds(), "scopeInstanceIds");
        }

        StringValidator.isEnumValue(req.getEnableStatus(), CommonConstant.ENABLE_STATUS, "enableStatus");

        StringValidator.notBlank(req.getCreatedBy(), "createdBy");


    }
}
