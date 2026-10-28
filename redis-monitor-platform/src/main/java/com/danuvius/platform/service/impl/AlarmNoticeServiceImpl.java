package com.danuvius.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.danuvius.platform.common.constant.CommonConstant;
import com.danuvius.platform.mapper.AlarmNoticeLogMapper;
import com.danuvius.platform.model.entity.AlarmNoticeLog;
import com.danuvius.platform.model.entity.AlarmRecord;
import com.danuvius.platform.model.entity.AlarmRule;
import com.danuvius.platform.model.enums.rule.AlarmLevelEnum;
import com.danuvius.platform.service.AlarmNoticeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 告警通知 Service 实现
 * 邮件发送依赖 spring-boot-starter-mail；通过 ObjectProvider 注入，
 * 未引入该依赖时服务仍可正常启动（仅记录"未发送"）。
 *
 * 配置示例（application.yml）：
 *   platform:
 *     alarm:
 *       notice:
 *         enabled: true
 *         receivers: ops@example.com,dev@example.com
 *   spring:
 *     mail:
 *       host: smtp.example.com
 *       username: notice@example.com
 *       password: xxxx
 */
@Slf4j
@Service
public class AlarmNoticeServiceImpl extends ServiceImpl<AlarmNoticeLogMapper, AlarmNoticeLog>
        implements AlarmNoticeService {

    /** 通知类型：1=邮件 */
    private static final String NOTICE_TYPE_MAIL = "1";
    /** 发送状态：0=失败 1=成功 2=未发送 */
    private static final String SEND_STATUS_FAIL = "0";
    private static final String SEND_STATUS_SUCCESS = "1";
    private static final String SEND_STATUS_SKIP = "2";

    private final AlarmNoticeLogMapper noticeLogMapper;
    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    @Value("${platform.alarm.notice.enabled:false}")
    private boolean noticeEnabled;

    @Value("${platform.alarm.notice.receivers:}")
    private List<String> receivers;

    public AlarmNoticeServiceImpl(AlarmNoticeLogMapper noticeLogMapper,
                                  ObjectProvider<JavaMailSender> mailSenderProvider) {
        this.noticeLogMapper = noticeLogMapper;
        this.mailSenderProvider = mailSenderProvider;
    }

    @Override
    public void sendAlarmNotice(AlarmRecord record, AlarmRule rule) {
        if (record == null) {
            return;
        }
        String receiverText = StringUtils.collectionToCommaDelimitedString(receivers);
        LocalDateTime now = LocalDateTime.now();

        // 未启用或无接收人：记录"未发送"
        if (!noticeEnabled || !StringUtils.hasText(receiverText)) {
            saveNoticeLog(record, receiverText, now, SEND_STATUS_SKIP, "邮件通知未启用或未配置接收人");
            return;
        }

        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            saveNoticeLog(record, receiverText, now, SEND_STATUS_SKIP, "邮件发送组件未配置（缺少 JavaMailSender）");
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(receivers.toArray(new String[0]));
            message.setSubject("【Redis监控告警】" + buildSubject(record));
            message.setText(buildMailContent(record, rule));
            mailSender.send(message);
            saveNoticeLog(record, receiverText, now, SEND_STATUS_SUCCESS, null);
            log.info("[告警通知] 邮件发送成功, alarmNo={}, receivers={}", record.getAlarmNo(), receiverText);
        } catch (Exception e) {
            saveNoticeLog(record, receiverText, now, SEND_STATUS_FAIL, e.getMessage());
            log.error("[告警通知] 邮件发送失败, alarmNo={}", record.getAlarmNo(), e);
        }
    }

    /** 查询某条告警的全部通知记录，按发送时间倒序 */
    @Override
    public List<AlarmNoticeLog> listByAlarmRecordId(String alarmRecordId) {
        List<AlarmNoticeLog> list=noticeLogMapper.selectList(
          new LambdaQueryWrapper<AlarmNoticeLog>()
                  .eq(AlarmNoticeLog::getAlarmRecordId, alarmRecordId)
                  .orderByDesc(AlarmNoticeLog::getSendTime)
        );
        return list;
    }

    private String buildSubject(AlarmRecord record) {
        String level = AlarmLevelEnum.descOf(record.getAlarmLevel());
        return level + " - " + record.getAlarmNo();
    }

    private String buildMailContent(AlarmRecord record, AlarmRule rule) {
        StringBuilder sb = new StringBuilder();
        sb.append("告警编号：").append(record.getAlarmNo()).append("\n");
        sb.append("告警级别：").append(AlarmLevelEnum.descOf(record.getAlarmLevel())).append("\n");
        sb.append("告警内容：").append(record.getAlarmContent()).append("\n");
        sb.append("触发实例：").append(record.getInstanceId()).append("\n");
        sb.append("触发时间：").append(record.getTriggerTime() == null
                ? "" : record.getTriggerTime().format(CommonConstant.FORMATTER)).append("\n");
        if (rule != null) {
            sb.append("关联规则：").append(rule.getRuleName()).append("\n");
            sb.append("比较方式：").append(rule.getCompareType())
                    .append(" 阈值：").append(rule.getThreshold())
                    .append(rule.getUnit() == null ? "" : rule.getUnit()).append("\n");
        }
        sb.append("\n请及时登录 Redis 监控平台处理。");
        return sb.toString();
    }

    private void saveNoticeLog(AlarmRecord record, String receiver, LocalDateTime sendTime,
                               String sendStatus, String failReason) {
        try {
            AlarmNoticeLog noticeLog = new AlarmNoticeLog();

            noticeLog.setAlarmRecordId(record.getId());
            noticeLog.setAlarmNo(record.getAlarmNo());
            noticeLog.setNoticeType(NOTICE_TYPE_MAIL);
            noticeLog.setReceiver(receiver);
            noticeLog.setSendTime(sendTime);
            noticeLog.setSendStatus(sendStatus);
            noticeLog.setFailReason(failReason);
            noticeLog.setCreatedTime(LocalDateTime.now());
            noticeLogMapper.insert(noticeLog);
        } catch (Exception e) {
            log.error("[告警通知] 通知记录写入失败, alarmNo={}", record.getAlarmNo(), e);
        }
    }
}
