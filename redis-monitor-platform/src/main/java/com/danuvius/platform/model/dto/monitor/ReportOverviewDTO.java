package com.danuvius.platform.model.dto.monitor;

import com.danuvius.platform.model.dto.alarm.record.AlarmStatsDTO;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 日周报概览统计
 */
@Data
public class ReportOverviewDTO implements Serializable {

    private String cpuAvg;
    private String cpuMax;
    private LocalDateTime cpuMaxTim;

    private String redisMemAvg;
    private String redisMemMax;
    private LocalDateTime redisMemMaxTim;

    private String qpsAvg;
    private String qpsMax;
    private LocalDateTime qpsMaxTim;

    private String latencyAvg;
    private String latencyMax;
    private LocalDateTime latencyMaxTim;


    /** 告警总数+分级统计dto */
    private AlarmStatsDTO alarmStatsDTO;

    /** 主从切换总次数 */
    private Long switchTotal;

    /** 最近切换时间，无则返回 null */
    private LocalDateTime lastSwitchTime;

    /** Redis服务可用率百分比（节点可用率） */
    private BigDecimal serviceAvailableRate;

    /** 主节点数 */
    private Long masterCount;

    /** 从节点数 */
    private Long replicaCount;
}
