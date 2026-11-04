package com.danuvius.platform.service;

import com.danuvius.platform.model.dto.monitor.NodeStatusResponse;
import com.danuvius.platform.model.dto.monitor.TopologyResponse;

import java.util.List;

/**
 * 主从拓扑与节点状态查询。
 *
 * <p>从 {@code RedisInstanceService} 拆出：控制层只依赖这两个读接口方法，
 * 不关心实例本身的增删改。</p>
 */
public interface TopologyQueryService {

    /**
     * 主从拓扑
     */
    TopologyResponse getTopology();

    /**
     * 主从状态表格
     */
    List<NodeStatusResponse> getNodesStatus();
}
