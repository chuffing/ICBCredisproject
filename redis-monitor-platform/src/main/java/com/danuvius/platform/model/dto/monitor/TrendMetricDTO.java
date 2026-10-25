package com.danuvius.platform.model.dto.monitor;

import lombok.Data;
import java.io.Serializable;
import java.util.List;

/**
 * 指标趋势
 */
@Data
public class TrendMetricDTO implements Serializable {
    /** 指标 key，如 cpuUsage / netInputBandwidth */
    private String metricKey;
    /** 指标名称 */
    private String metricName;
    /** 指标单位 */
    private String unit;
    /** 每个实例一条序列 */
    private List<TrendSeriesDTO> seriesList;
}