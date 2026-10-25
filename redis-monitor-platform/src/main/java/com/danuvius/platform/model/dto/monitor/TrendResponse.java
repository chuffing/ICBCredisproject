package com.danuvius.platform.model.dto.monitor;

import lombok.Data;
import java.io.Serializable;
import java.util.List;

@Data
public class TrendResponse implements Serializable {
    /** 统一 x 轴时间戳数组，全指标共用 */
    private List<String> timestampList;
    /** 各指标趋势，每个元素对应一个指标 */
    private List<TrendMetricDTO> metricList;
}