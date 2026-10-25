package com.danuvius.platform.model.dto.metric;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/** 单个核心实时指标 */
@Data
public class MetricCurrentValueDTO implements Serializable {
    private String metricKey;
    private String metricName;
    private BigDecimal currentValue;
    private String unit;
    /** 正常 / 提示 / 警告 / 严重 / 无数据 */
    private String metricStatus;
    /** 环比 5 分钟变化率（%） */
    private BigDecimal  changeRate5min;
}