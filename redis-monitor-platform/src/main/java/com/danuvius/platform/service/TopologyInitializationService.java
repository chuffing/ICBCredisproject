package com.danuvius.platform.service;

import com.danuvius.platform.model.dto.TopologyInitializationRequestDTO;
import com.danuvius.platform.model.vo.TopologyInitializationCandidateVO;
import com.danuvius.platform.model.vo.TopologyInitializationResponseVO;

import java.util.List;

/**
 * 首次拓扑纳管服务。
 */
public interface TopologyInitializationService {

    List<TopologyInitializationCandidateVO> listCandidates();

    TopologyInitializationResponseVO initialize(
            TopologyInitializationRequestDTO request);
}
