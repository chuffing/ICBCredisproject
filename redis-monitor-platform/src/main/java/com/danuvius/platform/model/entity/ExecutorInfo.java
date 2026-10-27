package com.danuvius.platform.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Date;

/**
 * 执行器注册及运行状态实体。
 */
@Data
@TableName("executor_info")
public class ExecutorInfo {

    /*执行器id*/
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    /*执行器绑定的 Redis 实例id，雪花*/
    private String instanceId;
    /*执行器所在服务器IP*/
    private String hostIp;
    /*执行器 HTTP 服务端口*/
    private String executorPort;
    /*执行器状态  0=OFFLINE / 1=ONLINE*/
    private String executorStatus;
    /*最近一次心跳时间*/
    private LocalDateTime lastHeartbeatTime;
    /*最近1次redis/Sentinel状态上报时间*/
    private LocalDateTime lastReportTime;
    /*纳管状态：0=INACTIVE / 1=ACTIVE*/
    private String managementStatus;
    /*认证 token*/
    private String executorToken;
    /*创建时间*/
    private LocalDateTime createdTime;
    /*最后更新时间*/
    private LocalDateTime updatedTime;
}
