package com.redisops.platform.redisinfo.entity;

import lombok.Data;
import lombok.ToString;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.LocalDateTime;

/**
 * redis_info表对应的正式纳管Redis实例。
 */
@TableName("redis_info")
@Data
public class RedisInfo {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String executorId;
    private String hostIp;
    private Integer redisPort;
    private Integer sentinelPort;
    private String redisRole;
    private String redisVersion;
    private String masterHost;
    private Integer masterPort;
    private String redisStatus;
    private String sentinelStatus;
    @ToString.Exclude
    @JsonIgnore
    private String redisPasswordCiphertext;
    private String managementStatus;
    private LocalDateTime lastCheckTime;
    private LocalDateTime managedTime;
    private LocalDateTime removedTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

}
