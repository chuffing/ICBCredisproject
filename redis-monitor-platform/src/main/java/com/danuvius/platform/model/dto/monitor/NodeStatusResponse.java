package com.danuvius.platform.model.dto.monitor;

import lombok.Data;

import java.io.Serializable;

@Data
public class NodeStatusResponse implements Serializable {
    /** 实例id */
    private String instanceId;
    /** 实例名 */
    private String instanceName;
    /** 节点IP */
    private String ip;
    /** 节点端口 */
    private String port;
    /** 节点角色：MASTER / REPLICA */
    private String role;
    /** 所属主节点地址 */
    private String masterName;
    /** 复制延迟 */
    private String copyDelay;
    /** 复制状态：正常 / 延迟 */
    private String copyStatus;
    /** 执行器 ping 平台的 HTTP 往返延迟（毫秒），无采集数据时为 null */
    private String pingLatency;
    /** 运行状态：正常 / 警告 / 严重 / 离线 */
    private String runStatus;
}