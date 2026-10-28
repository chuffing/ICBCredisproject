package com.danuvius.platform.model.enums.metric;

import lombok.Getter;

/**
 * 采集数据定义
 */
@Getter
public enum MetricTypeEnum {

    /*采集指标*/
    CPU_USAGE("cpuUsage", "CPU使用率", "%"),
    SERVER_MEM_USAGE("serverMemUsage", "服务器内存使用率", "%"),
    REDIS_MEM_USED("redisMemUsed", "Redis内存使用量", "GB"),
    QPS("qps", "QPS", "次/秒"),
    AVG_LATENCY("avgLatency", "平均延迟", "μs"),
    NET_INPUT_BANDWIDTH("netInputBandwidth", "网络输入带宽", "KB/s"),
    NET_OUTPUT_BANDWIDTH("netOutputBandwidth", "网络输出带宽", "KB/s"),

    /*其余监控指标*/
    NODE_RUN_STATUS("nodeRunStatus","节点存活状态",""),
    SENTINEL_STATUS("sentinelStatus","哨兵状态",""),
    REPL_DELAY("replDelay","主从复制延迟","s"),
    PING_LATENCY("pingLatency","连通性延迟","ms");


    /**指标 key*/
    private final String key;

    /**指标中文名*/
    private final String name;

    /**单位*/
    private final String unit;

    MetricTypeEnum(String key, String name, String unit) {
        this.key = key;
        this.name = name;
        this.unit = unit;
    }

    /**
     * 根据 metricKey 查找枚举。
     */
    public static MetricTypeEnum of(String key) {
        for (MetricTypeEnum item : values()) {
            if (item.key.equals(key)) {
                return item;
            }
        }
        return null;
    }
}