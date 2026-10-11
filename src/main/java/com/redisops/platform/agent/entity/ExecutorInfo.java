package com.redisops.platform.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * executor_info 表对应的执行器实体。
 */
@TableName("executor_info")
@Data
public class ExecutorInfo {

    @TableId(type = IdType.AUTO)
    /** 数据库主键。 */
    private Long id;
    /** 执行器编号。 */
    private String executorId;
    /** 执行器名称。 */
    private String executorName;
    /** 主机名。 */
    private String hostname;
    /** 主机 IP。 */
    private String hostIp;
    /** 执行器端口。 */
    private Integer executorPort;
    /** 操作系统名称。 */
    private String osName;
    /** 在线状态。 */
    private String executorStatus;
    /** 管理状态。 */
    private String managementStatus;
    /** 加密令牌。 */
    private String tokenCiphertext;
    /** 最后心跳时间。 */
    private LocalDateTime lastHeartbeatTime;
    /** 最后上报时间。 */
    private LocalDateTime lastReportTime;
    /** 注册时间。 */
    private LocalDateTime registerTime;
    /** 创建时间。 */
    private LocalDateTime createTime;
    /** 更新时间。 */
    private LocalDateTime updateTime;

    /** 获取加密令牌；该字段只供平台内部认证使用，不能返回给前端。 */
    @JsonIgnore
    public String getTokenCiphertext() { return tokenCiphertext; }

}
