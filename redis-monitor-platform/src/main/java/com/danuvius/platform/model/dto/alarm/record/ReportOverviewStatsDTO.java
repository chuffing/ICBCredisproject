package com.danuvius.platform.model.dto.alarm.record;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ReportOverviewStatsDTO implements Serializable {
    private BigDecimal cpuAvg;
    private BigDecimal cpuMax;
    private LocalDateTime cpuMaxTim;

    private BigDecimal redisMemAvg;
    private BigDecimal redisMemMax;
    private LocalDateTime redisMemMaxTim;

    private BigDecimal qpsAvg;
    private BigDecimal qpsMax;
    private LocalDateTime qpsMaxTim;

    private BigDecimal latencyAvg;
    private BigDecimal latencyMax;
    private LocalDateTime latencyMaxTim;

    private Long collectCount;
    private Long cpuCollectCount;
    private Long memCollectCount;
    private Long qpsCollectCount;
    private Long latencyCollectCount;
}
