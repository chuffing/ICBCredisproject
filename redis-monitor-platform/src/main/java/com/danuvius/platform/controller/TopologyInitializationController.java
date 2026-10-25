package com.danuvius.platform.controller;

import cn.hutool.core.lang.Validator;
import com.danuvius.platform.common.constant.TopologyInitializationConstants;
import com.danuvius.platform.common.core.ResultBean;
import com.danuvius.platform.common.exception.ParameterErrorCode;
import com.danuvius.platform.common.exception.ParameterException;
import com.danuvius.platform.model.dto.TopologyInitializationRequestDTO;
import com.danuvius.platform.model.vo.TopologyInitializationCandidateVO;
import com.danuvius.platform.model.vo.TopologyInitializationResponseVO;
import com.danuvius.platform.service.TopologyInitializationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
        return ResultBean.success(initializationService.listCandidates());
    }

    /**
     * 将用户选择的三台服务器正式纳管。
     */
    @PostMapping
    public ResultBean<TopologyInitializationResponseVO> initialize(
            @RequestBody TopologyInitializationRequestDTO request) {

        validateRequest(request);
        return ResultBean.success(initializationService.initialize(request));
    }

    /**
     * 检查首次纳管请求参数。
     */
    private void validateRequest(TopologyInitializationRequestDTO request) {

        // 1. 前端必须传入三台服务器IP。
        if (request == null
                || request.getHostIps() == null
                || request.getHostIps().size()
                != TopologyInitializationConstants.REQUIRED_NODE_COUNT) {
            throw new ParameterException(
                    ParameterErrorCode.TASK_PARAM_INVALID,
                    "必须选择三台待纳管服务器"
            );
        }

        // 2. 检查IP格式，并去除首尾空格。
        List<String> hostIps = new ArrayList<>();
        Set<String> uniqueHostIps = new HashSet<>();
        for (String value : request.getHostIps()) {
            String hostIp = value == null ? null : value.trim();
            if (!Validator.isIpv4(hostIp)) {
                throw new ParameterException(
                        ParameterErrorCode.TASK_PARAM_INVALID,
                        "服务器IP格式不正确"
                );
            }

            // 3. 同一台服务器不能被重复选择。
            if (!uniqueHostIps.add(hostIp)) {
                throw new ParameterException(
                        ParameterErrorCode.TASK_PARAM_INVALID,
                        "三台服务器IP不能重复"
                );
            }
            hostIps.add(hostIp);
        }

        request.setHostIps(hostIps);
    }
}
