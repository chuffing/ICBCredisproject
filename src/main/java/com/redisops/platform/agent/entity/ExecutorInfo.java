package com.redisops.platform.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.LocalDateTime;

/**
 * executor_info 表对应的执行器实体。
 */
@TableName("executor_info")
public class ExecutorInfo {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String executorId;
    private String executorName;
    private String hostname;
    private String hostIp;
    private Integer executorPort;
    private String osName;
    private String executorStatus;
    private String managementStatus;
    private String tokenCiphertext;
    private LocalDateTime lastHeartbeatTime;
    private LocalDateTime lastReportTime;
    private LocalDateTime registerTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /** 获取数据库主键。 */
    public Long getId() { return id; }

    /** 设置数据库主键。 */
    public void setId(Long id) { this.id = id; }

    /** 获取执行器编号。 */
    public String getExecutorId() { return executorId; }

    /** 设置执行器编号。 */
    public void setExecutorId(String executorId) { this.executorId = executorId; }

    /** 获取执行器名称。 */
    public String getExecutorName() { return executorName; }

    /** 设置执行器名称。 */
    public void setExecutorName(String executorName) { this.executorName = executorName; }

    /** 获取主机名。 */
    public String getHostname() { return hostname; }

    /** 设置主机名。 */
    public void setHostname(String hostname) { this.hostname = hostname; }

    /** 获取主机 IP。 */
    public String getHostIp() { return hostIp; }

    /** 设置主机 IP。 */
    public void setHostIp(String hostIp) { this.hostIp = hostIp; }

    /** 获取执行器端口。 */
    public Integer getExecutorPort() { return executorPort; }

    /** 设置执行器端口。 */
    public void setExecutorPort(Integer executorPort) { this.executorPort = executorPort; }

    /** 获取操作系统名称。 */
    public String getOsName() { return osName; }

    /** 设置操作系统名称。 */
    public void setOsName(String osName) { this.osName = osName; }

    /** 获取在线状态。 */
    public String getExecutorStatus() { return executorStatus; }

    /** 设置在线状态。 */
    public void setExecutorStatus(String executorStatus) { this.executorStatus = executorStatus; }

    /** 获取管理状态。 */
    public String getManagementStatus() { return managementStatus; }

    /** 设置管理状态。 */
    public void setManagementStatus(String managementStatus) { this.managementStatus = managementStatus; }

    /** 获取加密令牌；该字段只供平台内部认证使用，不能返回给前端。 */
    @JsonIgnore
    public String getTokenCiphertext() { return tokenCiphertext; }

    /** 设置加密令牌。 */
    public void setTokenCiphertext(String tokenCiphertext) { this.tokenCiphertext = tokenCiphertext; }

    /** 获取最后心跳时间。 */
    public LocalDateTime getLastHeartbeatTime() { return lastHeartbeatTime; }

    /** 设置最后心跳时间。 */
    public void setLastHeartbeatTime(LocalDateTime lastHeartbeatTime) { this.lastHeartbeatTime = lastHeartbeatTime; }

    /** 获取最后上报时间。 */
    public LocalDateTime getLastReportTime() { return lastReportTime; }

    /** 设置最后上报时间。 */
    public void setLastReportTime(LocalDateTime lastReportTime) { this.lastReportTime = lastReportTime; }

    /** 获取注册时间。 */
    public LocalDateTime getRegisterTime() { return registerTime; }

    /** 设置注册时间。 */
    public void setRegisterTime(LocalDateTime registerTime) { this.registerTime = registerTime; }

    /** 获取创建时间。 */
    public LocalDateTime getCreateTime() { return createTime; }

    /** 设置创建时间。 */
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    /** 获取更新时间。 */
    public LocalDateTime getUpdateTime() { return updateTime; }

    /** 设置更新时间。 */
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
