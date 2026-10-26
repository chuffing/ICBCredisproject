package com.danuvius.platform.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sentinel_node_info")
public class SentinelNodeInfo {
    /*主键id*/
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    /*哨兵节点名*/
    private String nodeName;
    /*哨兵节点IP*/
    private String nodeIp;
    /*哨兵节点端口*/
    private String nodePort;
    /*哨兵状态：0=离线/1=在线*/
    private String sentinelStatus;

    /** Sentinel故障恢复备份配置文件的SHA256摘要。 */
    private String configSha256;

    /*最后心跳时间*/
    private LocalDateTime lastHeartbeatTime;

    /*纳管状态：0=未纳管/1=已纳管*/
    private String managementStatus;
    /*创建时间*/
    private LocalDateTime createdTime;
    /*最后更新时间*/
    private LocalDateTime updatedTime;
}
