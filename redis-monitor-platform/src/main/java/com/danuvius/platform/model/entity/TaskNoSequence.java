package com.danuvius.platform.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 运维任务编号每日流水。
 */
@Data
@TableName("task_no_sequence")
public class TaskNoSequence {

    /** 流水号所属业务日期。 */
    @TableId(type = IdType.INPUT)
    private LocalDate bizDate;

    /** 当天已经分配的最大流水号。 */
    private Integer currentValue;

    /** 创建时间。 */
    private LocalDateTime createdTime;

    /** 更新时间。 */
    private LocalDateTime updatedTime;
}
