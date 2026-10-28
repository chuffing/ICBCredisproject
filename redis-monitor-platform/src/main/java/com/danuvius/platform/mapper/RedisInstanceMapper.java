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


    /**
     * 查询趋势数据
     * @param instanceIds
     * @param bucketMinutes 分桶分钟数:近1h=5，近6h=15
     * @param startTime
     * @param endTime
     * @return
     */
    List<Map<String, Object>> selectTrendData(@Param("instanceIds") List<String> instanceIds,
                                              @Param("bucketMinutes") int bucketMinutes,
                                              @Param("startTime") LocalDateTime startTime,
                                              @Param("endTime") LocalDateTime endTime);

    /* 日周报概览统计：4项指标均值/峰值/峰值时间 + 采集点数 */
    ReportOverviewStatsDTO selectReportOverviewStats(@Param("instanceId") String instanceId,
                                                     @Param("startTime") LocalDateTime startTime,
                                                     @Param("endTime") LocalDateTime endTime);

    /* 日周报4项指标趋势序列,当天按小时聚合，近7天按天聚合 */
    List<Map<String, Object>> selectReportTrend(@Param("instanceId") String instanceId,
                                                @Param("startTime") LocalDateTime startTime,
                                                @Param("endTime") LocalDateTime endTime,
                                                @Param("bucketMinutes") int bucketMinutes,
                                                @Param("truncateDay") boolean truncateDay);




}