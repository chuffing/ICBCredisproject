package com.danuvius.platform.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Date;

@Data
@TableName("redis_instance")
public class RedisInstance {
    /*主键id*/
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    /*实例id（雪花id）*/
    private String instanceId;
    /*实例名称*/
    private String instanceName;
    /*服务器IP*/
    private String hostIp;
    /*Redis端口*/
    private String redisPort;
    /*节点角色：MASTER/REPLICA*/
    private String role;
    /*Redis版本号*/
    private String redisVersion;
    /*运行状态：0=离线/1=正常/2=警告/3=严重*/
    private String runStatus;
    /*主节点IP（从节点有值，主节点默认''）*/
    @TableField(insertStrategy = FieldStrategy.NOT_NULL)
    private String masterHostIp;
    /*主节点Redis端口（从节点有值，主节点默认''）*/
    @TableField(insertStrategy = FieldStrategy.NOT_NULL)
    private String masterRedisPort;
    /*Redis故障恢复备份配置文件的SHA256摘要，非null，默认'' */
    private String configSha256;
    /*最近心跳时间*/
    private LocalDateTime lastHeartbeatTime;

    /*纳管状态：0=未纳管/1=已纳管*/
    private String managementStatus;
    /*创建时间*/
    private LocalDateTime createdTime;
    /*最后更新时间*/
    private LocalDateTime updatedTime;

}