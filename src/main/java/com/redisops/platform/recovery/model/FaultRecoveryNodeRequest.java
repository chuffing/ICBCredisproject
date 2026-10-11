package com.redisops.platform.recovery.model;

import lombok.Data;

/**
 * 故障恢复中一台服务器的执行器信息。
 */
@Data
public class FaultRecoveryNodeRequest {

    /** 负责本机操作的执行器编号 */
    private String executorId;
}
