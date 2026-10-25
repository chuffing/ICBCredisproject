package com.danuvius.platform.controller;

import cn.hutool.http.HttpStatus;
import com.danuvius.platform.common.core.ResultBean;
import com.danuvius.platform.model.dto.ServerSwitchRequestDTO;
import com.danuvius.platform.model.vo.ServerSwitchResponseVO;
import com.danuvius.platform.service.ServerSwitchService;
import com.danuvius.platform.util.validate.OperationCommonValidator;
import com.danuvius.platform.util.validate.ServerSwitchRequestValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Redis服务器切换接口。
 */
@RestController
@RequestMapping("/api/serverSwitch")
@RequiredArgsConstructor
public class ServerSwitchController {

    private final ServerSwitchService serverSwitchService;

    /**
     * 创建完整的Redis服务器切换任务。
     */
    @PostMapping
    public ResultBean<ServerSwitchResponseVO> switchServer(
            @RequestBody ServerSwitchRequestDTO request) {

        ServerSwitchRequestValidator.validate(request);

        ServerSwitchResponseVO response = serverSwitchService.switchServer(request);

        return new ResultBean<>(
                HttpStatus.HTTP_ACCEPTED,
                "服务器切换任务已接收",
                response
        );
    }

    /**
     * 查询服务器切换任务状态。
     */
    @GetMapping("/{taskNo}")
    public ResultBean<ServerSwitchResponseVO> getSwitchStatus(
            @PathVariable String taskNo) {

        String normalizedTaskNo =
                OperationCommonValidator.validateTaskNo(taskNo);

        return ResultBean.success(
                serverSwitchService.getSwitchStatus(normalizedTaskNo)
        );
    }

}
