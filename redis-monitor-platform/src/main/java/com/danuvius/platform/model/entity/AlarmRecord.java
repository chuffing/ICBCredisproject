package com.danuvius.platform.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Date;

/**
 * 告警记录实体。
 */
@Data
@TableName("alarm_record")
public class AlarmRecord {
    /** 主键id */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    /*告警编号*/
    private String alarmNo;

    /*关联告警规则id*/
    private String ruleId;

    /*告警触发实例id*/
    private String instanceId;

    /*监控指标 key*/
    private String metricKey;

    /*监控指标中文名称*/
    private String metricName;

    /*告警级别：1=提示/2=警告/3=严重*/
    private String alarmLevel;

    /*告警描述内容*/
    private String alarmContent;

    /*触发时指标值*/
    private String triggerValue;

    /*告警阈值*/
    private String threshold;

    /*阈值单位*/
    private String unit;

    /*告警触发时间*/
    private LocalDateTime triggerTime;

    /*告警恢复时间，未恢复为 null*/
    private LocalDateTime recoverTime;

    /*告警状态："0"=未恢复 / "1"=已恢复*/
    private String recoverStatus;

    /*创建时间*/
    private LocalDateTime createdTime;
    /*最后更新时间*/
    private LocalDateTime updatedTime;
}
