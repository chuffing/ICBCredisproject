package com.danuvius.platform.util;

import com.danuvius.platform.common.constant.CommonConstant;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 趋势/报告时间分桶工具
 * 实时趋势与日周报统一分桶口径
 */
public final class TimeBucketUtil {

    private TimeBucketUtil() {
    }

    /**
     * 生成对齐后的统一时间轴（yyyy-MM-dd HH:mm:ss，升序）
     */
    public static List<String> alignedTimestamps(LocalDateTime start, LocalDateTime end, int bucketMinutes) {
        List<String> timestamps = new ArrayList<>();
        LocalDateTime cursor = truncateToBucket(start, bucketMinutes);
        while (!cursor.isAfter(end)) {
            timestamps.add(cursor.format(CommonConstant.FORMATTER));
            cursor = cursor.plusMinutes(bucketMinutes);
        }
        return timestamps;
    }

    /**
     * 将时间按分桶粒度向下取整（与 SQL bucketExpr 口径一致）
     */
    public static LocalDateTime truncateToBucket(LocalDateTime time, int bucketMinutes) {
        int minuteOfHour = time.getMinute();
        int aligned = (minuteOfHour / bucketMinutes) * bucketMinutes;
        return time.withMinute(aligned).withSecond(0).withNano(0);
    }

    /**
     * 统一时间桶格式：JDBC 可能返回 Timestamp/LocalDateTime/String，统一为 yyyy-MM-dd HH:mm:ss
     */
    public static String formatBucket(Object bucket) {
        if (bucket == null) {
            return null;
        }
        if (bucket instanceof LocalDateTime) {
            return ((LocalDateTime) bucket).format(CommonConstant.FORMATTER);
        }
        // Timestamp 是 Date 子类，toInstant() 保留纳秒
        if (bucket instanceof Date) {
            return LocalDateTime
                    .ofInstant(((Date) bucket).toInstant(), ZoneId.systemDefault())
                    .format(CommonConstant.FORMATTER);
        }
        //截断19位
        String text = bucket.toString();
        return (text.length() > 19 && text.charAt(19) == '.')
                ? text.substring(0, 19)
                : text;
    }
}
