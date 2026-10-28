package com.danuvius.platform.common.exception.validator;

import com.danuvius.platform.common.constant.CommonConstant;
import com.danuvius.platform.common.exception.ParameterCode;
import com.danuvius.platform.common.exception.ParameterException;
import org.apache.commons.lang3.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

/**
 * 日期 / 时间参数校验。
 */
public final class DateValidator {


    private DateValidator() {
    }

    /** 字符串按日期格式校验（null/空返回 null，不抛异常） */
    public static LocalDateTime parseDateTime(String value, String fieldName) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        try {
            return LocalDateTime.parse(value.trim(), CommonConstant.FORMATTER);
        } catch (DateTimeParseException e) {
            throw new ParameterException(ParameterCode.PARAM_DATE_FORMAT_ERROR,
                    "字段[" + fieldName + "]日期格式必须为yyyy-MM-dd HH:mm:ss");
        }
    }

    /** 字符串按日期格式校验（yyyy-MM-dd） */
    public static LocalDate parseDate(String value, String fieldName) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim(), CommonConstant.DATE_FMT);
        } catch (DateTimeParseException e) {
            throw new ParameterException(ParameterCode.PARAM_DATE_FORMAT_ERROR,
                    "字段[" + fieldName + "]日期格式必须为yyyy-MM-dd");
        }
    }

    /** 时间范围：start <= end */
    public static void range(LocalDateTime start, LocalDateTime end, String startField, String endField) {
        if (start != null && end != null && start.isAfter(end)) {
            throw new ParameterException(ParameterCode.PARAM_DATE_RANGE_ERROR,
                    "字段[" + startField + "]不能晚于字段[" + endField + "]");
        }
    }

    /** 时间范围：字符串版，校验格式 + 顺序 */
    public static void stringRange(String start, String end, String startField, String endField) {
        LocalDateTime s = parseDateTime(start, startField);
        LocalDateTime e = parseDateTime(end, endField);
        range(s, e, startField, endField);
    }

    /** 时间不晚于当前时间 */
    public static void notFuture(LocalDateTime value, String fieldName) {
        if (value != null && value.isAfter(LocalDateTime.now())) {
            throw new ParameterException(ParameterCode.PARAM_DATE_RANGE_ERROR,
                    "字段[" + fieldName + "]不能晚于当前时间");
        }
    }

    /** 时间不早于当前时间 */
    public static void notPast(LocalDateTime value, String fieldName) {
        if (value != null && value.isBefore(LocalDateTime.now())) {
            throw new ParameterException(ParameterCode.PARAM_DATE_RANGE_ERROR,
                    "字段[" + fieldName + "]不能早于当前时间");
        }
    }
}