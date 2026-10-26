package com.danuvius.platform.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.danuvius.platform.model.dto.agent.SentinelNodeReportDTO;
import com.danuvius.platform.model.dto.monitor.SentinelItemResponse;
import com.danuvius.platform.model.entity.ExecutorInfo;
import com.danuvius.platform.model.entity.SentinelNodeInfo;

import java.util.List;

public interface SentinelNodeInfoService extends IService<SentinelNodeInfo> {
    /**
     * Sentinel列表
     * @param managementStatus
     * @return
     */
    List<SentinelItemResponse> getSentinelList(String managementStatus);



    /**
     * 处理随指标一起上报的 Sentinel 节点：按 nodeIp + nodePort upsert。
     * @param executor 已通过 token 校验的执行器
     * @param nodes    哨兵节点列表，可为空
     * @return 本次处理的节点数
     */
    int reportNodes(ExecutorInfo executor, List<SentinelNodeReportDTO> nodes);
}
