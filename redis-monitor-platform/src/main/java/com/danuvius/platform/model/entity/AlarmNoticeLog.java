package com.danuvius.platform.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 告警通知记录实体。
 */
@Data
@TableName("alarm_notice_log")
public class AlarmNoticeLog {
    /** 通知记录id */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 告警记录id，对应alarm_record的id */
    private String alarmRecordId;

    /** 告警编号 */
    private String alarmNo;

    /** 通知类型：1=邮件 */
    private String noticeType;

    /** 接收人，多个逗号分隔 */
    private String receiver;

    /** 发送时间 */
    private LocalDateTime sendTime;

    /** 发送状态：0=失败 1=成功 2=未发送 */
    private String sendStatus;

    /** 失败原因，成功为 null */
    private String failReason;

    /** 创建时间。 */
    private LocalDateTime createdTime;
}
