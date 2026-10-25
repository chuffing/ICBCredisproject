package com.danuvius.platform.util;

import java.util.List;

public class CacheKeyUtil {

    public static final String TOPOLOGY = "cache:sentinel:topology";
    public static final String NODES = "cache:sentinel:nodes";

    public static String instanceList(String managementStatus) {
        return "cache:instance:list:" + managementStatus;
    }

    public static String realtimeCurrent(String instanceId) {
        return "cache:realtime:current:" + instanceId;
    }

    public static String realtimeTrend(List<String> instanceIds, String timeRange) {
        return "cache:realtime:trend:" + String.join(",", instanceIds) + ":" + timeRange;
    }

    public static String report(String instanceId, String reportType) {
        return "cache:report:" + instanceId + ":" + reportType;
    }

    public static String sentinelList(String managementStatus) {
        return "cache:sentinel:list:" + managementStatus;
    }
}