package com.danuvius.platform.util;

import com.danuvius.platform.common.constant.CommonConstant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;

@Slf4j
@Component
public class PdfUtils {
    //报告格式化

    public static final String reportRange(String reportType) {
        LocalDate today = LocalDate.now();
        if (CommonConstant.REPORT_TYPE_WEEK.equals(reportType)) {
            return today.minusDays(6).format(CommonConstant.DATE_FMT) + " 至 " + today.format(CommonConstant.DATE_FMT);
        }
        return today.format(CommonConstant.DATE_FMT);
    }

    public static final String percent(Object value) {
        return decimal(value, 1);
    }

    public static final String decimal(Object value, int scale) {
        if (value == null || String.valueOf(value).trim().isEmpty()) {
            return "--";
        }
        try {
            return new BigDecimal(String.valueOf(value)).setScale(scale, RoundingMode.HALF_UP)
                    .toPlainString();
        } catch (Exception e) {
            return "--";
        }
    }

    public static final String integer(Object value) {
        if (value == null || String.valueOf(value).trim().isEmpty()) {
            return "--";
        }
        try {
            return String.format(Locale.US, "%,d",
                    new BigDecimal(String.valueOf(value)).longValue());
        } catch (Exception e) {
            return "--";
        }
    }

    public static final String withUnit(String value, String unit) {
        return "--".equals(value) ? value : value + unit;
    }

    public static final String timeSuffix(LocalDateTime time) {
        return time == null ? "" : "｜" + time.format(CommonConstant.TIME_FMT);
    }

    public static final String timeText(LocalDateTime time) {
        return time == null ? "--" : time.format(CommonConstant.TIME_FMT);
    }

    public static final long longValue(Long value) {
        return value == null ? 0L : value;
    }

    public static final String axisValue(double value) {
        if (Math.abs(value) >= 100) {
            return String.format(Locale.US, "%.0f", value);
        }
        return String.format(Locale.US, "%.1f", value);
    }

    public static final String shortTime(String timestamp, String reportType) {
        if (timestamp == null) {
            return "";
        }
        String text = timestamp.trim();
        if (text.length() >= 16) {
            return CommonConstant.REPORT_TYPE_WEEK.equals(reportType) ? text.substring(5, 16) : text.substring(11, 16);
        }
        return text;
    }

    public static final String text(Object value) {
        return value == null || String.valueOf(value).trim().isEmpty()
                ? "--" : String.valueOf(value);
    }


    public static final void appendCard(StringBuilder sb, String name, Object avg, Object max, LocalDateTime maxTime) {
        sb.append("<div class=\"card\"><div>").append(escape(name)).append("</div>")
                .append("<div class=\"avg\">").append(nullSafe(avg)).append("</div>")
                .append("<div class=\"peak\">峰值 ").append(nullSafe(max))
                .append("　").append(escape(fmt(maxTime))).append("</div></div>");
    }

    public static final void appendPlainCard(StringBuilder sb, String name, Object value) {
        sb.append("<div class=\"card\"><div>").append(escape(name)).append("</div>")
                .append("<div class=\"avg\">").append(nullSafe(value)).append("</div></div>");
    }


    public static final String nullSafe(Object value) {
        return value == null ? "-" : value.toString();
    }

    public static final String fmt(LocalDateTime time) {
        return time == null ? "" : time.format(CommonConstant.FORMATTER);
    }


    public static final String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
