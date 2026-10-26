package com.danuvius.platform.model.dto.agent;

import lombok.Data;

import java.io.Serializable;

/**
 * 执行器上报的单个 Sentinel 节点信息
 */
@Data
public class SentinelNodeReportDTO implements Serializable {

    /** 哨兵节点名，执行器未提供时平台使用 ip:port 生成 */
    private String nodeName;

    /** 哨兵节点 IP */
    private String nodeIp;

    /** 哨兵节点端口 */
    private String nodePort;

    /** 哨兵状态：0=离线 / 1=在线 */
    private String sentinelStatus;

    /** 该哨兵监听的 master 名称（仅用于日志追踪，平台暂不落库） */
    private String masterName;
}
