package com.redisops.platform.redisinfo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.LocalDateTime;

/**
 * redis_info表对应的正式纳管Redis实例。
 */
@TableName("redis_info")
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
    private String redisPasswordCiphertext;
    private String managementStatus;
    private LocalDateTime lastCheckTime;
    private LocalDateTime managedTime;
    private LocalDateTime removedTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /** 获取主键。 */ public Long getId() { return id; }
    /** 设置主键。 */ public void setId(Long value) { id = value; }
    /** 获取执行器编号。 */ public String getExecutorId() { return executorId; }
    /** 设置执行器编号。 */ public void setExecutorId(String value) { executorId = value; }
    /** 获取服务器IP。 */ public String getHostIp() { return hostIp; }
    /** 设置服务器IP。 */ public void setHostIp(String value) { hostIp = value; }
    /** 获取Redis端口。 */ public Integer getRedisPort() { return redisPort; }
    /** 设置Redis端口。 */ public void setRedisPort(Integer value) { redisPort = value; }
    /** 获取Sentinel端口。 */ public Integer getSentinelPort() { return sentinelPort; }
    /** 设置Sentinel端口。 */ public void setSentinelPort(Integer value) { sentinelPort = value; }
    /** 获取正式拓扑角色。 */ public String getRedisRole() { return redisRole; }
    /** 设置正式拓扑角色。 */ public void setRedisRole(String value) { redisRole = value; }
    /** 获取最近验收版本。 */ public String getRedisVersion() { return redisVersion; }
    /** 设置最近验收版本。 */ public void setRedisVersion(String value) { redisVersion = value; }
    /** 获取正式主节点地址。 */ public String getMasterHost() { return masterHost; }
    /** 设置正式主节点地址。 */ public void setMasterHost(String value) { masterHost = value; }
    /** 获取正式主节点端口。 */ public Integer getMasterPort() { return masterPort; }
    /** 设置正式主节点端口。 */ public void setMasterPort(Integer value) { masterPort = value; }
    /** 获取最近Redis验收状态。 */ public String getRedisStatus() { return redisStatus; }
    /** 设置最近Redis验收状态。 */ public void setRedisStatus(String value) { redisStatus = value; }
    /** 获取最近Sentinel验收状态。 */ public String getSentinelStatus() { return sentinelStatus; }
    /** 设置最近Sentinel验收状态。 */ public void setSentinelStatus(String value) { sentinelStatus = value; }
    /** 获取Redis密码密文，仅供平台内部使用。 */
    @JsonIgnore
    public String getRedisPasswordCiphertext() { return redisPasswordCiphertext; }
    /** 设置Redis密码密文。 */ public void setRedisPasswordCiphertext(String value) { redisPasswordCiphertext = value; }
    /** 获取纳管状态。 */ public String getManagementStatus() { return managementStatus; }
    /** 设置纳管状态。 */ public void setManagementStatus(String value) { managementStatus = value; }
    /** 获取最近运维验收时间。 */ public LocalDateTime getLastCheckTime() { return lastCheckTime; }
    /** 设置最近运维验收时间。 */ public void setLastCheckTime(LocalDateTime value) { lastCheckTime = value; }
    /** 获取正式纳管时间。 */ public LocalDateTime getManagedTime() { return managedTime; }
    /** 设置正式纳管时间。 */ public void setManagedTime(LocalDateTime value) { managedTime = value; }
    /** 获取退出纳管时间。 */ public LocalDateTime getRemovedTime() { return removedTime; }
    /** 设置退出纳管时间。 */ public void setRemovedTime(LocalDateTime value) { removedTime = value; }
    /** 获取创建时间。 */ public LocalDateTime getCreateTime() { return createTime; }
    /** 设置创建时间。 */ public void setCreateTime(LocalDateTime value) { createTime = value; }
    /** 获取更新时间。 */ public LocalDateTime getUpdateTime() { return updateTime; }
    /** 设置更新时间。 */ public void setUpdateTime(LocalDateTime value) { updateTime = value; }
}
