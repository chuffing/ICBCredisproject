package com.redisops.platform.agent.status.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * executor_runtime_status 表对应的执行器最新运行状态。
 *
 * <p>每台执行器只保留一行最新数据，历史监控指标以后由监控模块单独保存。</p>
 */
@TableName("executor_runtime_status")
public class ExecutorRuntimeStatus {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String executorId;
    private String redisStatus;
    private String redisRole;
    private String redisVersion;
    private String redisMasterHost;
    private Integer redisMasterPort;
    private String redisErrorMessage;
    private String sentinelStatus;
    private String sentinelMasterName;
    private String sentinelMasterHost;
    private Integer sentinelMasterPort;
    private String sentinelErrorMessage;
    private Long collectTime;
    private LocalDateTime receivedTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /** 获取主键。 */ public Long getId() { return id; }
    /** 设置主键。 */ public void setId(Long value) { this.id = value; }
    /** 获取执行器编号。 */ public String getExecutorId() { return executorId; }
    /** 设置执行器编号。 */ public void setExecutorId(String value) { this.executorId = value; }
    /** 获取Redis状态。 */ public String getRedisStatus() { return redisStatus; }
    /** 设置Redis状态。 */ public void setRedisStatus(String value) { this.redisStatus = value; }
    /** 获取Redis角色。 */ public String getRedisRole() { return redisRole; }
    /** 设置Redis角色。 */ public void setRedisRole(String value) { this.redisRole = value; }
    /** 获取Redis版本。 */ public String getRedisVersion() { return redisVersion; }
    /** 设置Redis版本。 */ public void setRedisVersion(String value) { this.redisVersion = value; }
    /** 获取Redis主节点地址。 */ public String getRedisMasterHost() { return redisMasterHost; }
    /** 设置Redis主节点地址。 */ public void setRedisMasterHost(String value) { this.redisMasterHost = value; }
    /** 获取Redis主节点端口。 */ public Integer getRedisMasterPort() { return redisMasterPort; }
    /** 设置Redis主节点端口。 */ public void setRedisMasterPort(Integer value) { this.redisMasterPort = value; }
    /** 获取Redis异常说明。 */ public String getRedisErrorMessage() { return redisErrorMessage; }
    /** 设置Redis异常说明。 */ public void setRedisErrorMessage(String value) { this.redisErrorMessage = value; }
    /** 获取Sentinel状态。 */ public String getSentinelStatus() { return sentinelStatus; }
    /** 设置Sentinel状态。 */ public void setSentinelStatus(String value) { this.sentinelStatus = value; }
    /** 获取Sentinel监控名称。 */ public String getSentinelMasterName() { return sentinelMasterName; }
    /** 设置Sentinel监控名称。 */ public void setSentinelMasterName(String value) { this.sentinelMasterName = value; }
    /** 获取Sentinel记录的主节点地址。 */ public String getSentinelMasterHost() { return sentinelMasterHost; }
    /** 设置Sentinel记录的主节点地址。 */ public void setSentinelMasterHost(String value) { this.sentinelMasterHost = value; }
    /** 获取Sentinel记录的主节点端口。 */ public Integer getSentinelMasterPort() { return sentinelMasterPort; }
    /** 设置Sentinel记录的主节点端口。 */ public void setSentinelMasterPort(Integer value) { this.sentinelMasterPort = value; }
    /** 获取Sentinel异常说明。 */ public String getSentinelErrorMessage() { return sentinelErrorMessage; }
    /** 设置Sentinel异常说明。 */ public void setSentinelErrorMessage(String value) { this.sentinelErrorMessage = value; }
    /** 获取执行器采集时间戳。 */ public Long getCollectTime() { return collectTime; }
    /** 设置执行器采集时间戳。 */ public void setCollectTime(Long value) { this.collectTime = value; }
    /** 获取平台接收时间。 */ public LocalDateTime getReceivedTime() { return receivedTime; }
    /** 设置平台接收时间。 */ public void setReceivedTime(LocalDateTime value) { this.receivedTime = value; }
    /** 获取创建时间。 */ public LocalDateTime getCreateTime() { return createTime; }
    /** 设置创建时间。 */ public void setCreateTime(LocalDateTime value) { this.createTime = value; }
    /** 获取更新时间。 */ public LocalDateTime getUpdateTime() { return updateTime; }
    /** 设置更新时间。 */ public void setUpdateTime(LocalDateTime value) { this.updateTime = value; }
}