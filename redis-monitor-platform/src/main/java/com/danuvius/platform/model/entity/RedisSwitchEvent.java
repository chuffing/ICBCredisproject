package com.danuvius.platform.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Redis主从切换事件实体。
 */
@Data
@TableName("redis_switch_event")
public class RedisSwitchEvent {
    /*主键id*/
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    /*原主节点实例id*/
    private String oldMasterId;
    /*新主节点实例id*/
    private String newMasterId;
    /*切换发生时间*/
    private LocalDateTime switchTime;
    /*切换类型：0=基线/1=自动切换/2=手动切换/3=其他*/
    private String switchType;
    /*切换结果：0=失败/1=成功*/
    private String switchResult;
    /*创建时间*/
    private LocalDateTime createdTime;
    /*最后更新时间*/
    private LocalDateTime updatedTime;
}
