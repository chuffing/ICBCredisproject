package com.danuvius.platform.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 首次拓扑纳管候选服务器。
 */
@Data
@AllArgsConstructor
public class TopologyInitializationCandidateVO {

    /** 候选服务器IP。 */
    private String hostIp;

    /** 候选服务器上的执行器编号。 */
    private String executorId;

    /** 候选服务器上的执行器服务端口。 */
    private String executorPort;

    /** 执行器在线状态。 */
    private String executorStatus;

    /** 执行器最近一次心跳时间。 */
    private LocalDateTime lastHeartbeatTime;

    /** 执行器最近一次指标上报时间。 */
    private LocalDateTime lastReportTime;
}
