package com.redisops.platform.agent.status.model;

/** 执行器周期状态上报请求。 */
public class MetricReportRequest {

    private String agentId;
    private MetricPayload metric;

    /** 获取执行器编号。 */ public String getAgentId() { return agentId; }
    /** 设置执行器编号。 */ public void setAgentId(String value) { this.agentId = value; }
    /** 获取本次采集结果。 */ public MetricPayload getMetric() { return metric; }
    /** 设置本次采集结果。 */ public void setMetric(MetricPayload value) { this.metric = value; }

    /** 与执行器 MetricData 对应的状态字段。 */
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

        /** 获取Redis角色。 */ public String getRole() { return role; }
        /** 设置Redis角色。 */ public void setRole(String value) { this.role = value; }
        /** 获取Redis版本。 */ public String getRedisVersion() { return redisVersion; }
        /** 设置Redis版本。 */ public void setRedisVersion(String value) { this.redisVersion = value; }
        /** 获取Redis主节点地址。 */ public String getMasterHost() { return masterHost; }
        /** 设置Redis主节点地址。 */ public void setMasterHost(String value) { this.masterHost = value; }
        /** 获取Redis主节点端口。 */ public String getMasterPort() { return masterPort; }
        /** 设置Redis主节点端口。 */ public void setMasterPort(String value) { this.masterPort = value; }
        /** 获取采集时间戳。 */ public Long getCollectTime() { return collectTime; }
        /** 设置采集时间戳。 */ public void setCollectTime(Long value) { this.collectTime = value; }
        /** 获取Redis状态。 */ public String getStatus() { return status; }
        /** 设置Redis状态。 */ public void setStatus(String value) { this.status = value; }
        /** 获取Redis异常说明。 */ public String getErrorMsg() { return errorMsg; }
        /** 设置Redis异常说明。 */ public void setErrorMsg(String value) { this.errorMsg = value; }
        /** 获取Sentinel状态。 */ public String getSentinelStatus() { return sentinelStatus; }
        /** 设置Sentinel状态。 */ public void setSentinelStatus(String value) { this.sentinelStatus = value; }
        /** 获取Sentinel监控名称。 */ public String getSentinelMasterName() { return sentinelMasterName; }
        /** 设置Sentinel监控名称。 */ public void setSentinelMasterName(String value) { this.sentinelMasterName = value; }
        /** 获取Sentinel主节点地址。 */ public String getSentinelMasterHost() { return sentinelMasterHost; }
        /** 设置Sentinel主节点地址。 */ public void setSentinelMasterHost(String value) { this.sentinelMasterHost = value; }
        /** 获取Sentinel主节点端口。 */ public Integer getSentinelMasterPort() { return sentinelMasterPort; }
        /** 设置Sentinel主节点端口。 */ public void setSentinelMasterPort(Integer value) { this.sentinelMasterPort = value; }
        /** 获取Sentinel异常说明。 */ public String getSentinelErrorMsg() { return sentinelErrorMsg; }
        /** 设置Sentinel异常说明。 */ public void setSentinelErrorMsg(String value) { this.sentinelErrorMsg = value; }
    }
}