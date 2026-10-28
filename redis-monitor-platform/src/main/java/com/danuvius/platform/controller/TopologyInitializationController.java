package com.danuvius.platform.controller;

import com.danuvius.platform.common.core.ResultBean;
import com.danuvius.platform.common.exception.ErrorCodeInfo;
import com.danuvius.platform.model.dto.TopologyInitializationRequestDTO;
import com.danuvius.platform.model.vo.TopologyInitializationCandidateVO;
import com.danuvius.platform.model.vo.TopologyInitializationResponseVO;
import com.danuvius.platform.service.TopologyInitializationService;
import com.danuvius.platform.util.validate.TopologyInitializationRequestValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 首次拓扑纳管接口。
 */
@RestController
@RequestMapping("/api/topologyInitialization")
@RequiredArgsConstructor
public class TopologyInitializationController {

    private final TopologyInitializationService initializationService;

    /**
     * 查询当前可以参与首次纳管的服务器。
     */
    @GetMapping("/candidates")
    public ResultBean<List<TopologyInitializationCandidateVO>> candidates() {
        return ResultBean.restResult(
                initializationService.listCandidates(),
                ErrorCodeInfo.OK
        );
    }

    /**
     * 将用户选择的三台服务器正式纳管。
     */
    @PostMapping
    public ResultBean<TopologyInitializationResponseVO> initialize(
            @RequestBody TopologyInitializationRequestDTO request) {

        TopologyInitializationRequestValidator.validate(request);
        return ResultBean.restResult(
                initializationService.initialize(request),
                ErrorCodeInfo.OK
        );
    }
}
