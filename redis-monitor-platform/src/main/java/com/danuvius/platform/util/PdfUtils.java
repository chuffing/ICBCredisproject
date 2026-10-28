package com.danuvius.platform.util;

import com.danuvius.platform.common.constant.CommonConstant;
import com.danuvius.platform.model.dto.monitor.ReportTrendDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Slf4j
@Component
public class PdfUtils {
    // ---------------------------------------------------------------- 报告格式化

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

//    /**
//     * 生成简易 SVG 折线图（无外部依赖）。
//     */
//    public static final String buildLineChartSvg(ReportTrendDTO trend) {
//        List<String> times = trend.getTimestampList();
//        List<Double> values = trend.getValueList();
//        if (times == null || values == null || times.isEmpty() || values.isEmpty()) {
//            return "<p>无数据</p>";
//        }
//        int width = 640;
//        int height = 220;
//        int padLeft = 50;
//        int padBottom = 30;
//        int padTop = 10;
//        int padRight = 10;
//
//        double min = Double.MAX_VALUE;
//        double max = -Double.MAX_VALUE;
//        for (Double v : values) {
//            if (v == null) {
//                continue;
//            }
//            min = Math.min(min, v);
//            max = Math.max(max, v);
//        }
//        if (min > max) {
//            min = 0;
//            max = 1;
//        }
//        if (min == max) {
//            max = min + 1;
//        }
//
//        int count = Math.min(times.size(), values.size());
//        double stepX = count > 1 ? (double) (width - padLeft - padRight) / (count - 1) : 0;
//
//        StringBuilder points = new StringBuilder();
//        for (int i = 0; i < count; i++) {
//            Double v = values.get(i);
//            if (v == null) {
//                continue;
//            }
//            double x = padLeft + i * stepX;
//            double y = padTop + (height - padTop - padBottom) * (1 - (v - min) / (max - min));
//            if (points.length() > 0) {
//                points.append(" ");
//            }
//            points.append(String.format("%.1f,%.1f", x, y));
//        }
//
//        StringBuilder svg = new StringBuilder();
//        svg.append("<svg width=\"").append(width).append("\" height=\"").append(height)
//                .append("\" viewBox=\"0 0 ").append(width).append(" ").append(height).append("\"")
//                .append(" xmlns=\"http://www.w3.org/2000/svg\" style=\"background:#fafafa;border:1px solid #eee;\">");
//        // 坐标轴
//        svg.append("<line x1=\"").append(padLeft).append("\" y1=\"").append(height - padBottom)
//                .append("\" x2=\"").append(width - padRight).append("\" y2=\"").append(height - padBottom)
//                .append("\" stroke=\"#999\"/>");
//        svg.append("<line x1=\"").append(padLeft).append("\" y1=\"").append(padTop)
//                .append("\" x2=\"").append(padLeft).append("\" y2=\"").append(height - padBottom)
//                .append("\" stroke=\"#999\"/>");
//        // 最大值/最小值标注
//        svg.append("<text x=\"").append(4).append("\" y=\"").append(padTop + 10)
//                .append("\" font-size=\"10\" fill=\"#666\">").append(String.format("%.2f", max)).append("</text>");
//        svg.append("<text x=\"").append(4).append("\" y=\"").append(height - padBottom)
//                .append("\" font-size=\"10\" fill=\"#666\">").append(String.format("%.2f", min)).append("</text>");
//        // 首尾时间标注
//        svg.append("<text x=\"").append(padLeft).append("\" y=\"").append(height - 10)
//                .append("\" font-size=\"10\" fill=\"#666\">").append(escape(times.get(0))).append("</text>");
//        svg.append("<text x=\"").append(width - padRight - 110).append("\" y=\"").append(height - 10)
//                .append("\" font-size=\"10\" fill=\"#666\">").append(escape(times.get(count - 1))).append("</text>");
//        // 折线
//        svg.append("<polyline points=\"").append(points).append("\" fill=\"none\" stroke=\"#d9534f\" stroke-width=\"1.5\"/>");
//        svg.append("</svg>");
//        return svg.toString();
//    }

    // ---------------------------------------------------------------- 工具

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
