package com.danuvius.platform.model.dto.alarm.rule;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 告警列表查询请求
 */
@Data
public class RuleListRequest implements Serializable {
    /*模糊查询关键字，匹配规则名称/监控指标*/
    private String keyword;
    /*告警级别筛选：1 提示 / 2 警告 / 3 严重*/
    private String alarmLevel;
    /*生效节点筛选，实例 id 列表*/
    private List<String> scopeInstanceIds;
    /*启用状态筛选：0 禁用 / 1 启用*/
    private String enableStatus;
    /*页码，默认 1*/
    private Integer pageNum = 1;
    /*每页条数，默认 12*/
    private Integer pageSize = 12;
}
