package com.danuvius.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.danuvius.platform.model.dto.alarm.record.ReportOverviewStatsDTO;
import com.danuvius.platform.model.dto.monitor.RedisInstanceItemDTO;
import com.danuvius.platform.model.entity.RedisInstance;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface RedisInstanceMapper extends BaseMapper<RedisInstance> {







}