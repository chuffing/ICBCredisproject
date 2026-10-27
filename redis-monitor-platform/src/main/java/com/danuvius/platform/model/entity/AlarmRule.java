package com.danuvius.platform.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Date;

/**
 * 告警规则实体。
 */
@Data
@TableName("alarm_rule")
public class AlarmRule {
    /*主键id*/
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    /*告警规则id，业务唯一键*/
    private String ruleId;


    /*告警规则名称*/
    private String ruleName;
    /*监控指标 key，如 cpuUsage、qps等*/
    private String metricKey;
    /*监控指标中文名称*/
    private String metricName;
    /*比较方式：>、<、=、>=、<=*/
    private String compareType;
    /*告警阈值*/
    private String threshold;
    /*阈值单位*/
    private String unit;
    /*采集间隔，单位秒，默认=300*/
    private String collectInterval;
    /*持续触发时长，单位个采集周期，默认=1*/
    private String duration;
    /*告警级别：1=提示/2=警告/3=严重*/
    private String alarmLevel;
    /*告警静默期，单位秒，默认 1800（30min）*/
    private String silenceDuration;
    /*生效范围：1=全部节点/2=指定节点*/
    private String scopeType;
    /*生效实例id串，逗号分隔，scopeType=2时有效*/
    private String scopeInstanceId;
    /*启用状态：0=禁用/1=启用*/
    private String enableStatus;
    /*逻辑删除标记：0=未删除/1=已删除*/
    @TableLogic(value = "0", delval = "1")
    private String delFlag;
    /*创建人*/
    private String createdBy;
    /*创建时间*/
    private LocalDateTime createdTime;
    /*最后更新时间*/
    private LocalDateTime updatedTime;
}
