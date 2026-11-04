package com.danuvius.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.danuvius.platform.model.dto.alarm.record.ReportOverviewStatsDTO;
import com.danuvius.platform.model.entity.MetricLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface MetricLogMapper extends BaseMapper<MetricLog> {
    /*清除7天前记录*/
    int deleteExpiredBefore(@Param("threshold") LocalDateTime threshold);

    /*按实例取最新一条指标，拓扑/节点状态判断*/
    MetricLog selectLatestByInstanceId(@Param("instanceId") String instanceId);

    /**
     * 查询某个实例某个指标在指定时间段内的采集时间序列。
     */
    List<Map<String, Object>> selectMetricTrend(@Param("instanceId") String instanceId,
                                                @Param("metricKey") String metricKey,
                                                @Param("startTime") LocalDateTime startTime,
                                                @Param("endTime") LocalDateTime endTime);

    /**
     * 取某个实例某个指标的最近 N 条采集值（用于持续时长判断），倒序。
     */
    List<String> selectRecentMetricValues(@Param("instanceId") String instanceId,
                                          @Param("metricKey") String metricKey,
                                          @Param("limit") int limit);


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
