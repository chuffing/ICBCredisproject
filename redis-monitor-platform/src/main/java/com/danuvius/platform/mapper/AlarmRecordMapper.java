package com.danuvius.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.danuvius.platform.model.dto.alarm.record.AlarmStatsDTO;
import com.danuvius.platform.model.dto.alarm.record.RecordItemResponse;
import com.danuvius.platform.model.dto.alarm.record.RecordOverviewResponse;
import com.danuvius.platform.model.entity.AlarmRecord;
import org.apache.commons.net.ntp.TimeStamp;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface AlarmRecordMapper extends BaseMapper<AlarmRecord> {
    /*最近未恢复*/
    List<RecordItemResponse> selectRecentUnrecovered(@Param("limit") int limit);
    /*最近记录*/
    List<RecordItemResponse> selectRecent(@Param("limit") int limit);
    /*记录概览数据*/
    RecordOverviewResponse selectRecordOverview(@Param("startTime") LocalDateTime startTime,
    /*记录分页*/                                            @Param("endTime") LocalDateTime endTime);
    IPage<RecordItemResponse> selectRecordPage(IPage<RecordItemResponse> page,
                                               @Param("instanceId") String instanceId,
                                               @Param("alarmLevel") String alarmLevel,
                                               @Param("metricKey") String metricKey,
                                               @Param("recoverStatus") String recoverStatus,
                                               @Param("keyword") String keyword,
                                               @Param("startTime") String startTime,
                                               @Param("endTime") String endTime);



    /*日周报告警明细：关联实例表补充节点 IP:Port 展示 */
    List<Map<String, Object>> selectReportEventList(@Param("instanceId") String instanceId,
                                                    @Param("startTime") String startTime,
                                                    @Param("endTime") String endTime,
                                                    @Param("limit") int limit);

    /*日周报告警统计：总数与分级统计 */
    AlarmStatsDTO selectReportAlarmStats(@Param("instanceId") String instanceId,
                                         @Param("startTime") String startTime,
                                         @Param("endTime") String endTime);


}