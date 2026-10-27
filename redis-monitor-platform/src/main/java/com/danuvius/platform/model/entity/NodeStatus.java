package com.danuvius.platform.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Date;

/**
 * Redis节点状态采集记录实体。
 */
@Data
@TableName("rms_node_status_log")
public class NodeStatus {

    /*节点状态记录id*/
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    /*实例id*/
    private String instanceId;
    /*redis运行id*/
    private String runId;
    /*采集时间*/
    private LocalDateTime tm;

    /*采集时节点状态，“0”=离线，“1”=在线*/
    private String collectStatus;
    /*失败原因*/
    private String errorMsg;
    /*角色*/
    private String role;
    /*连通性延迟*/
    private String pingLatency;
    /*创建时间*/
    private LocalDateTime createdTime;
    /*最后更新时间*/
    private LocalDateTime updatedTime;

}
