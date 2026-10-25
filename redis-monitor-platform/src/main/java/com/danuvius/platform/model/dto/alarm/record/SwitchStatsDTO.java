package com.danuvius.platform.model.dto.alarm.record;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class SwitchStatsDTO implements Serializable {
    private Long switchTotal;          // COUNT(*) 永远非 null，至少为 0
    private LocalDateTime lastSwitchTime; // 无切换时为 null
}
