package com.danuvius.platform.model.dto.monitor;

import lombok.Data;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 实例指标趋势
 */
@Data
public class TrendSeriesDTO implements Serializable {
    /** 实例 id */
    private String instanceId;
    /**
     * 时间点数据，每项固定为 {bucket: "时间戳", value: 数值或 null}；
     * 按 timestampList 顺序对齐，缺失点 value=null，供前端断点展示
     */
    private List<Map<String, Object>> points;
}