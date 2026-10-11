package com.redisops.platform.agent.status.entity;

import lombok.Data;
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
@Data
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
}