package com.danuvius.platform.model.dto.metric;

import com.danuvius.platform.model.dto.agent.SentinelNodeReportDTO;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/** Agent 上报的指标项 */
@Data
public class MetricReportItemDTO implements Serializable {
    /** 平台内部回填，Agent 不需要上报 */
    @JsonIgnore
    private String instanceId;

    /** 执行器认证 token */
    private String token;
    /** CPU 占用率（%） */
    private String cpuPer;
    /** 服务器内存占用率（%） */
    private String memServerPer;
    /** Redis 内存使用量（GB） */
    private String memUs;
    /** 命令加权平均延迟（微秒） */
    private String latency;
    /** 网络输入带宽（kbps） */
    private String ioIik;
    /** 网络输出带宽（kbps） */
    private String ioIok;
    /** 瞬时 QPS(次/秒) */
    private String ops;
    /** 节点角色：MASTER / REPLICA */
    private String role;
    /** Redis 版本号 */
    private String redisVersion;
    /** Redis 运行id */
    private String runId;
    /** 主节点 host（从节点上报） */
    private String masterHost;
    /** 主节点 port（从节点上报） */
    private String masterPort;
    /** 主从复制延迟（秒） */
    private String replDelay;

    /** 采集类型：0=自动，1=手动 */
    private String collectType;

    /** 连通性延迟，执行器端采集时上报 */
    private String pingLatency;

    /** 随本次指标一起上报的 Sentinel 节点列表 */
    private List<SentinelNodeReportDTO> sentinels;

    /** 采集失败原因 */
    private String errorMsg;
}