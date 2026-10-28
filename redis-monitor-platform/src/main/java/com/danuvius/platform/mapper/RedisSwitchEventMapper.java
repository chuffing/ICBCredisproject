package com.danuvius.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.danuvius.platform.model.dto.alarm.record.SwitchStatsDTO;
import com.danuvius.platform.model.entity.RedisSwitchEvent;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface RedisSwitchEventMapper extends BaseMapper<RedisSwitchEvent> {

    /* 日周报主从切换明细（关联实例表补充切换双方 IP:Port） */
    List<Map<String, Object>> selectSwitchEventList(@Param("startTime") LocalDateTime startTime,
                                                    @Param("endTime") LocalDateTime endTime,
                                                    @Param("limit") int limit);

    /* 日周报主从切换统计：总次数与最近切换时间 */
    SwitchStatsDTO selectSwitchStats(@Param("startTime") LocalDateTime startTime,
                                     @Param("endTime") LocalDateTime endTime);
}
