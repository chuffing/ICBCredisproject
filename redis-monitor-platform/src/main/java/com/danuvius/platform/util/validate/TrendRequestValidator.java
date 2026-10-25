package com.danuvius.platform.util.validate;

import com.danuvius.platform.common.constant.CommonConstant;
import com.danuvius.platform.common.exception.validator.ListValidator;
import com.danuvius.platform.common.exception.validator.StringValidator;
import com.danuvius.platform.model.dto.monitor.TrendRequest;

public class TrendRequestValidator {
    public static void validate(TrendRequest req) {
        ListValidator.noNullElement(req.getInstanceIds(), "instanceIds");
        ListValidator.maxSize(req.getInstanceIds(), 50, "instanceIds");

        StringValidator.notBlank(req.getTimeRange(), "timeRange");
        StringValidator.isEnumValue(req.getTimeRange(), CommonConstant.TIME_RANGES, "timeRange");
    }
}
