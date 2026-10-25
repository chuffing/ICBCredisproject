package com.danuvius.platform.model.dto.alarm.record;

import lombok.Data;

import java.io.Serializable;

/**
 * 告警总数+分级统计dto
 */
@Data
public class AlarmStatsDTO implements Serializable {
    private Long alarmTotal;
    private Long infoCount;
    private Long alarmCount;
    private Long severityCount;
}
