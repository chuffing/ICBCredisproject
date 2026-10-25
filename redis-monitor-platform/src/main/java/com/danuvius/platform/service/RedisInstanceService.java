package com.danuvius.platform.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.danuvius.platform.model.dto.monitor.*;
import com.danuvius.platform.model.entity.RedisInstance;

import java.io.File;
import java.time.LocalDateTime;
import java.util.List;

public interface RedisInstanceService extends IService<RedisInstance> {
    /**
     * 查实例列表
     * @param managementStatus
     * @return
     */
    List<RedisInstanceItemDTO> getInstanceList(String managementStatus);

    /**
     * 拉核心指标
     * @param instanceId
     * @return
     */
    MetricCurrentValueResponse getCurrentValues(String instanceId);

    /**
     * 曲线
     * @param req
     * @return
     */
    TrendResponse getTrend(TrendRequest req);



    /**
     * 主从拓扑
     * @return
     */
    TopologyResponse getTopology();

    /**
     * 节点状态
     * @return
     */
    List<NodeStatusResponse> getNodesStatus();

    /**
     * 日周报查询
     * @param req
     * @return
     */
    ReportQueryResponse queryReport(ReportQueryRequest req);

    /**
     * 组装完整报告数据，查询与导出共用
     * @param instanceId 实例id
     * @param reportType 报告类型：日报/周报
     */
    ReportQueryResponse buildReportData(String instanceId, String reportType);

    /**
     * 导出
     * @param instanceId
     * @param reportType
     * @return
     */
    ReportExportResponse exportReport(String instanceId, String reportType);

    /**
     * 查询导出任务状态，前端3s轮询
     */
    ReportExportResponse queryExportTask(String taskId);

    /**
     * 获取导出文件，任务成功后下载
     */
    File getExportFile(String taskId);

    /**
     * 更新实例表，心跳、运行状态
     * @param instanceId
     * @param now
     * @param runStatus
     * @return
     */
    Boolean updateHeartbeatAndStatus(String instanceId, LocalDateTime now, String runStatus);
}
