package com.redisops.platform.agent.configsnapshot.entity;

import lombok.Data;
import lombok.ToString;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.LocalDateTime;

/**
 * executor_config_snapshot 表对应的执行器基准配置快照。
 */
@TableName("executor_config_snapshot")
@Data
public class ExecutorConfigSnapshot {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String executorId;
    @ToString.Exclude
    @JsonIgnore
    private String redisConfigCiphertext;
    private String redisConfigSha256;
    @ToString.Exclude
    @JsonIgnore
    private String sentinelConfigCiphertext;
    private String sentinelConfigSha256;
    private String snapshotStatus;
    private LocalDateTime initializedTime;
    private LocalDateTime lastVerifiedTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

}
