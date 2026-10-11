package com.redisops.platform.agent.status.model;

import lombok.Data;

/** 执行器周期状态上报请求。 */
@Data
public class MetricReportRequest {

    private String agentId;
    private MetricPayload metric;

    /** 与执行器 MetricData 对应的状态字段。 */
    @Data
    public static class MetricPayload {
        private String role;
        private String redisVersion;
        private String masterHost;
        private String masterPort;
        private Long collectTime;
        private String status;
        private String errorMsg;
        private String sentinelStatus;
        private String sentinelMasterName;
        private String sentinelMasterHost;
        private Integer sentinelMasterPort;
        private String sentinelErrorMsg;
    }
}