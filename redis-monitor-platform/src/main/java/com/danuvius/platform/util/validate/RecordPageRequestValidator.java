package com.danuvius.platform.util.validate;

import com.danuvius.platform.common.constant.CommonConstant;
import com.danuvius.platform.common.exception.validator.*;
import com.danuvius.platform.model.dto.alarm.record.RecordPageRequest;
import org.apache.commons.lang3.StringUtils;

public class RecordPageRequestValidator {
    public static void validate(RecordPageRequest req){
        /*所有字段均非必输*/
        NumberValidator.valueBetween(req.getPageNum(), 1, 100, "pageNum");
        NumberValidator.valueBetween(req.getPageSize(), 1, 20, "pageSize");

        if(req.getInstanceId()!=null) {
            IdValidator.snowflake(req.getInstanceId(), "instanceId");
        }
        if(req.getAlarmLevel()!=null) {
            StringValidator.isEnumValue(req.getAlarmLevel(), CommonConstant.ALARM_LEVELS, "alarmLevel");
        }
        if(req.getRecoverStatus()!=null) {
            StringValidator.isEnumValue(req.getRecoverStatus(), CommonConstant.RECOVER_STATUS, "recoverStatus");
        }
        if(req.getMetricKey()!=null) {
            StringValidator.maxLength(req.getMetricKey(), 64, "metricKey");

        }
        if(req.getKeyword()!=null) {
            StringValidator.maxLength(req.getKeyword(), 128, "keyword");
        }

        DateValidator.range(req.getStartTime(), req.getEndTime(), "startTime", "endTime");
    }
}
