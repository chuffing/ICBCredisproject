package com.redisops.platform.agent.configsnapshot.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.LocalDateTime;

/**
 * executor_config_snapshot 表对应的执行器基准配置快照。
 */
@TableName("executor_config_snapshot")
public class ExecutorConfigSnapshot {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String executorId;
    private String redisConfigCiphertext;
    private String redisConfigSha256;
    private String sentinelConfigCiphertext;
    private String sentinelConfigSha256;
    private String snapshotStatus;
    private LocalDateTime initializedTime;
    private LocalDateTime lastVerifiedTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /** 获取数据库主键。 */ public Long getId() { return id; }
    /** 设置数据库主键。 */ public void setId(Long id) { this.id = id; }
    /** 获取执行器编号。 */ public String getExecutorId() { return executorId; }
    /** 设置执行器编号。 */ public void setExecutorId(String executorId) { this.executorId = executorId; }
    /** 获取Redis配置密文。 */ @JsonIgnore public String getRedisConfigCiphertext() { return redisConfigCiphertext; }
    /** 设置Redis配置密文。 */ public void setRedisConfigCiphertext(String value) { this.redisConfigCiphertext = value; }
    /** 获取Redis配置摘要。 */ public String getRedisConfigSha256() { return redisConfigSha256; }
    /** 设置Redis配置摘要。 */ public void setRedisConfigSha256(String value) { this.redisConfigSha256 = value; }
    /** 获取Sentinel配置密文。 */ @JsonIgnore public String getSentinelConfigCiphertext() { return sentinelConfigCiphertext; }
    /** 设置Sentinel配置密文。 */ public void setSentinelConfigCiphertext(String value) { this.sentinelConfigCiphertext = value; }
    /** 获取Sentinel配置摘要。 */ public String getSentinelConfigSha256() { return sentinelConfigSha256; }
    /** 设置Sentinel配置摘要。 */ public void setSentinelConfigSha256(String value) { this.sentinelConfigSha256 = value; }
    /** 获取快照状态。 */ public String getSnapshotStatus() { return snapshotStatus; }
    /** 设置快照状态。 */ public void setSnapshotStatus(String value) { this.snapshotStatus = value; }
    /** 获取首次初始化时间。 */ public LocalDateTime getInitializedTime() { return initializedTime; }
    /** 设置首次初始化时间。 */ public void setInitializedTime(LocalDateTime value) { this.initializedTime = value; }
    /** 获取最近校验时间。 */ public LocalDateTime getLastVerifiedTime() { return lastVerifiedTime; }
    /** 设置最近校验时间。 */ public void setLastVerifiedTime(LocalDateTime value) { this.lastVerifiedTime = value; }
    /** 获取创建时间。 */ public LocalDateTime getCreateTime() { return createTime; }
    /** 设置创建时间。 */ public void setCreateTime(LocalDateTime value) { this.createTime = value; }
    /** 获取更新时间。 */ public LocalDateTime getUpdateTime() { return updateTime; }
    /** 设置更新时间。 */ public void setUpdateTime(LocalDateTime value) { this.updateTime = value; }
}
