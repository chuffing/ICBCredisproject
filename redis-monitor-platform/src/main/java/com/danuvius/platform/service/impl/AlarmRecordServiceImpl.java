package com.danuvius.platform.service.impl;


import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.danuvius.platform.common.constant.CommonConstant;
import com.danuvius.platform.common.exception.BusinessCode;
import com.danuvius.platform.common.exception.BusinessException;
import com.danuvius.platform.mapper.*;
import com.danuvius.platform.model.dto.alarm.record.*;
import com.danuvius.platform.model.entity.AlarmRecord;
import com.danuvius.platform.model.entity.AlarmRule;
import com.danuvius.platform.model.entity.AuditLog;
import com.danuvius.platform.model.entity.RedisInstance;
import com.danuvius.platform.service.AlarmNoticeService;
import com.danuvius.platform.service.AlarmRecordService;
import com.danuvius.platform.service.AuditLogService;
import com.danuvius.platform.util.ParseUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class AlarmRecordServiceImpl extends ServiceImpl<AlarmRecordMapper, AlarmRecord>
        implements AlarmRecordService {
    private final AlarmRecordMapper alarmRecordMapper;
    private final AlarmRuleMapper alarmRuleMapper;
    private final MetricLogMapper metricLogMapper;
    private final AuditLogService auditLogService;

    /*告警详情需关联实例信息与通知记录 */
    private final RedisInstanceMapper redisInstanceMapper;
    private final AlarmNoticeService alarmNoticeService;
    private final AuditLogMapper auditLogMapper;


    public AlarmRecordServiceImpl(AlarmRuleMapper alarmRuleMapper, AlarmRecordMapper alarmRecordMapper,
                                  MetricLogMapper metricLogMapper, RedisInstanceMapper redisInstanceMapper,
                                  AuditLogService auditLogService, AlarmNoticeService alarmNoticeService,
                                  AuditLogMapper auditLogMapper) {
        this.alarmRecordMapper = alarmRecordMapper;
        this.alarmRuleMapper = alarmRuleMapper;
        this.metricLogMapper = metricLogMapper;
        this.redisInstanceMapper = redisInstanceMapper;
        this.auditLogService = auditLogService;
        this.alarmNoticeService = alarmNoticeService;
        this.auditLogMapper = auditLogMapper;
    }
    @Override
    public List<RecordItemResponse> getRecentUnrecovered() {
        List<RecordItemResponse> list = alarmRecordMapper.selectRecentUnrecovered(CommonConstant.DEFAULT_RECENT);

        return CollectionUtils.isEmpty(list) ? new ArrayList<>() : list;
    }


    @Override
    public List<RecordItemResponse> getRecent() {
        List<RecordItemResponse> list = alarmRecordMapper.selectRecent(CommonConstant.DEFAULT_RECENT);
        return CollectionUtils.isEmpty(list) ? Collections.emptyList() : list;

    }

    @Override
    public RecordOverviewResponse getRecordOverview() {
        LocalDateTime end = LocalDateTime.now();
        LocalDateTime start = end.toLocalDate().minusDays(3).atStartOfDay();

        return alarmRecordMapper.selectRecordOverview(start, end);
    }

    /**
     * 记录分页查询
     * @param req
     * @return
     */
    @Override
    public RecordPageResponse getRecordPage(RecordPageRequest req) {
        int pageNum = req.getPageNum() == null || req.getPageNum() < 1 ? 1 : req.getPageNum();
        int pageSize = req.getPageSize() == null || req.getPageSize() < 1 ? 12 : req.getPageSize();

        String instanceId=req.getInstanceId();
        String alarmLevel=req.getAlarmLevel();
        String metricKey=req.getMetricKey();
        String recoverStatus=req.getRecoverStatus();
        String keyword=req.getKeyword();
        LocalDateTime startTime=req.getStartTime();
        LocalDateTime endTime=req.getEndTime();

        //查
        Page<RecordItemResponse> page = new Page<>(pageNum, pageSize);
        alarmRecordMapper.selectRecordPage(page,instanceId, alarmLevel, metricKey, recoverStatus, keyword, startTime, endTime);

        log.info("告警记录查询结果：{}",page.toString());
        RecordPageResponse resp = new RecordPageResponse();
        resp.setPage(page);
        return resp;
    }

    /**
     * 记录详情
     * @param alarmRecordId
     * @return
     */
    @Override
    public RecordDetailResponse getRecordDetail(String alarmRecordId) {
        AlarmRecord record = alarmRecordMapper.selectById(alarmRecordId);
        if (record == null) throw new BusinessException(BusinessCode.NOT_FOUND_ERROR.getCode(), "告警记录不存在");

        RecordDetailResponse dto = new RecordDetailResponse();
        dto.setAlarmBaseInfo(record);
        if (record.getRuleId() != null) {
            AlarmRule rule = alarmRuleMapper.selectOne(Wrappers.<AlarmRule>lambdaQuery()
                    .eq(AlarmRule::getRuleId, record.getRuleId()));
            dto.setRuleInfo(rule);
        }

        /* 关联节点信息表查询实例名、ip、端口、角色 */
        if (record.getInstanceId() != null) {
            RedisInstance redisInstance = redisInstanceMapper.selectOne(Wrappers.<RedisInstance>lambdaQuery()
                    .eq(RedisInstance::getInstanceId, record.getInstanceId()));
            dto.setInstanceInfo(redisInstance);
        }
        /* 查询通知发送记录表，返回邮件通知发送时间、接收人、发送状态 */
        dto.setNoticeLogs(alarmNoticeService.listByAlarmRecordId(record.getId()));
        /*人工确认恢复后，从操作审计日志填充处理人信息*/
        fillOperatorInfo(dto, alarmRecordId);
        return dto;
    }

    /**
     * 详情曲线
     * @param alarmRecordId
     * @return
     */
    @Override
    public RecordMetricTrendResponse getMetricTrend(String alarmRecordId) {
        AlarmRecord record = alarmRecordMapper.selectById(alarmRecordId);
        if (record == null) throw new BusinessException(BusinessCode.NOT_FOUND_ERROR.getCode(), "告警记录不存在");
        if (record.getTriggerTime() == null) {
            throw new BusinessException(BusinessCode.PARAMS_ERROR.getCode(),"告警记录缺少告警触发时间");
        }

        RecordMetricTrendResponse dto = new RecordMetricTrendResponse();
        dto.setMetricName(record.getMetricName());
        dto.setThresholdValue(record.getThreshold());
        dto.setUnit(record.getUnit());
        dto.setTriggerTime(record.getTriggerTime());
        dto.setRecoverTime(record.getRecoverTime());

        dto.setTimestampList(new ArrayList<>());
        dto.setValueList(new ArrayList<>());



        //曲线序列开始时间：触发时间-30min
        LocalDateTime start = record.getTriggerTime().minusMinutes(30);
        LocalDateTime end;
        //未恢复：当前时间，已恢复：恢复时间+10min
        if (CommonConstant.RECOVER.equals(record.getRecoverStatus()) && record.getRecoverTime() != null) {
            end = record.getRecoverTime().plusMinutes(10);
        } else {
            end = LocalDateTime.now();
        }

        //拉时间序列
        List<Map<String, Object>> rows = metricLogMapper.selectMetricTrend(record.getInstanceId(), record.getMetricKey(), start, end);

        List<String> timestampList = new ArrayList<>();
        List<Double> valueList = new ArrayList<>();
        if (rows != null) {
            for (Map<String, Object> row : rows) {
                Object tm = row.get("tm");
                if (tm == null) {
                    continue;
                }
                timestampList.add(tm.toString());
                Object val = row.get("value");
                valueList.add(val == null ? null : ParseUtils.stringToDouble(val.toString()));
            }
        }
        dto.setTimestampList(timestampList);
        dto.setValueList(valueList);
        return dto;
    }

    /**
     * 从操作审计日志填充告警处理人信息（人工确认恢复记录）
     * @param dto
     * @param alarmRecordId
     */
    private void fillOperatorInfo(RecordDetailResponse dto, String alarmRecordId) {
        try {
            AuditLog latest = auditLogMapper.selectOne(Wrappers.<AuditLog>lambdaQuery()
                    .eq(AuditLog::getTargetId, alarmRecordId)
                    .eq(AuditLog::getOperationType,CommonConstant.OPERATION_TYPE_ALARM_RECOVERY)
                    .orderByDesc(AuditLog::getOperationTime)
                    .last("LIMIT 1")
            );

            if (latest != null) {
                dto.setOperatorId(latest.getOperatorId());
                dto.setOperatorName(latest.getOperatorName());
                dto.setOperationTime(latest.getOperationTime() == null
                        ? null : latest.getOperationTime().format(CommonConstant.FORMATTER));
                dto.setOperationMsg(latest.getOperationContent());
            }
        } catch (Exception e) {
            // 处理人信息获取失败不影响详情主流程
            log.warn("[告警详情] 获取处理人信息失败, alarmRecordId={}", alarmRecordId, e);
        }
    }


    /**
     * 告警人工确认恢复：
     * 1) 校验告警记录存在；
     * 2) 已恢复则幂等返回 true，不重复写审计日志；
     * 3) 更新 recover_status=1、recover_time=当前时间、updated_time=当前时间；
     * 4) 记入操作审计日志（module=告警记录，operationType=恢复）。
     */
    @Override
    public boolean recoverAlarm(String alarmRecordId) {
        AlarmRecord record = alarmRecordMapper.selectById(alarmRecordId);
        if (record == null) {
            throw new BusinessException(BusinessCode.NOT_FOUND_ERROR.getCode(), "告警记录不存在");
        }
        //已恢复幂等返回，不重复写审计日志
        if (CommonConstant.RECOVER.equals(record.getRecoverStatus())) {
            return true;
        }


        LocalDateTime now = LocalDateTime.now();
        int rows = alarmRecordMapper.update(null,
                new LambdaUpdateWrapper<AlarmRecord>()
                        .set(AlarmRecord::getRecoverStatus, CommonConstant.RECOVER)
                        .set(AlarmRecord::getRecoverTime,now)
                        .set(AlarmRecord::getUpdatedTime,now)
                        .eq(AlarmRecord::getId, record.getId())
        );


        if (rows > 0) {
            //人工确认恢复记入操作审计日志
            auditLogService.record(CommonConstant.MODULE_ALARM_RECORD, CommonConstant.OPERATION_TYPE_ALARM_RECOVERY, alarmRecordId,
                    "人工确认恢复告警：" + record.getAlarmNo());
        }
        return rows > 0;
    }
}
