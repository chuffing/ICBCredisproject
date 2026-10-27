package com.danuvius.platform.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Redis实例信息实体。
 */
@Data
@TableName("redis_instance")
public class RedisInstance {

    /** 数据库内部主键。 */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** Redis实例业务编号，用于关联执行器和Sentinel。 */
    private String instanceId;

    /** Redis实例名称。 */
    private String instanceName;

    /** Redis所在服务器IP。 */
    private String hostIp;

    /** Redis服务端口。 */
    private String redisPort;

    /** Redis当前角色：MASTER或REPLICA。 */
    private String role;

    /** Redis版本号。 */
    private String redisVersion;

    /** Redis运行状态。 */
    private String runStatus;

    /** 从节点当前连接的主节点IP。 */
    private String masterHostIp;

    /** 从节点当前连接的主节点Redis端口。 */
    private String masterRedisPort;

    /** Redis故障恢复备份配置文件的SHA256摘要。 */
    private String configSha256;

    /** 最近一次心跳时间。 */
    private LocalDateTime lastHeartbeatTime;

    /** 纳管状态：0=未纳管，1=已纳管。 */
    private String managementStatus;

    /** 创建时间。 */
    private LocalDateTime createdTime;

    /** 更新时间。 */
    private LocalDateTime updatedTime;

}
