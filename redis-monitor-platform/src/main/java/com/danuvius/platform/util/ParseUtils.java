package com.danuvius.platform.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

@Slf4j
@Component
public class ParseUtils {
    /*String转Integer*/
    public static Integer stringToInteger(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException e) {
            log.warn("非法整数字符串: {}", value);
            return null;
        }
    }

    /*String转Double*/
    public static Double stringToDouble(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return Double.valueOf(value.trim());
        } catch (NumberFormatException e) {
            log.warn("非法浮点数字符串: {}", value);
            return null;
        }
    }

    /*Double转String*/
    public static String doubleToString(Double value) {
        return value != null ? String.valueOf(value) : null;
    }

    /*duration非数字或空，取值1*/
    public static int parseDuration(String duration) {
        if (duration == null || duration.trim().isEmpty()) {
            return 1;
        }
        try {
            return Integer.parseInt(duration.trim());
        } catch (Exception e) {
            return 1;
        }
    }

    public static String joinIds(List<String> ids) {
        return ids == null ? "" : String.join(",", ids);
    }


}
