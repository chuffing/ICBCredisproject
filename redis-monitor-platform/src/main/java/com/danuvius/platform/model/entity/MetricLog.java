package com.danuvius.platform.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Date;

/**
 * Redis运行指标采集记录实体。
 */
@Data
@TableName("rms_metric_log")
public class MetricLog {

    /*采集记录id*/
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    /*实例id*/
    private String instanceId;
    /*采集时间*/
    private LocalDateTime tm;

    /*CPU 占用率（%）*/
    private String cpuPer;
    /*服务器内存占用率（%）*/
    private String memServerPer;
    /*Redis 内存使用量（GB）*/
    private String memUs;
    /*命令加权平均延迟（微秒）*/
    private String latency;
    /*网络输入带宽（kbps）*/
    private String ioIik;
    /*网络输出带宽（kbps）*/
    private String ioIok;
    /*瞬时 QPS(次/秒)*/
    private String ops;
    /*主从复制延迟（秒）*/
    private String replDelay;

    /*Redis 运行id*/
    private String runId;
    /*主节点 host（从节点上报）*/
    private String masterHost;
    /*主节点 port（从节点上报）*/
    private String masterPort;
    /*节点角色：MASTER / REPLICA*/
    private String role;
    /*Redis 版本号*/
    private String redisVersion;

    /*采集类型：0=自动，1=手动*/
    private String collectType;
    /*创建时间*/
    private LocalDateTime createdTime;
    /*最后更新时间*/
    private LocalDateTime updatedTime;
}
