package com.danuvius.platform.controller;

import com.danuvius.platform.common.BaseResponse;
import com.danuvius.platform.common.ResultUtils;
import com.danuvius.platform.common.exception.BusinessCode;
import com.danuvius.platform.model.dto.agent.AgentHeartbeatRequest;
import com.danuvius.platform.model.dto.agent.AgentRegisterRequest;
import com.danuvius.platform.model.dto.agent.AgentRegisterResultDTO;
import com.danuvius.platform.model.dto.executor.ExecutorManagementRequest;
import com.danuvius.platform.service.ExecutorInfoService;
import com.danuvius.platform.util.validate.AgentHeartbeatRequestValidator;
import com.danuvius.platform.util.validate.AgentRegisterRequestValidator;
import com.danuvius.platform.util.validate.ExecutorManagementRequestValidator;
import org.springframework.web.bind.annotation.*;

import javax.validation.constraints.NotBlank;

@RestController
@RequestMapping("/api/agent")

public class ExecutorController {

    private final ExecutorInfoService executorInfoService;

    public ExecutorController(ExecutorInfoService executorInfoService) {
        this.executorInfoService = executorInfoService;
    }

    @PostMapping("/register")
    public BaseResponse<AgentRegisterResultDTO> register( @RequestBody AgentRegisterRequest req) {
        AgentRegisterRequestValidator.validate(req);

        try {
            AgentRegisterResultDTO result = executorInfoService.register(
                    req.getRegisterCode(),
                    req.getHostIp(), req.getExecutorPort(), req.getRedisInfo(),req.getRedisReady());
            return ResultUtils.success(result);
        } catch (Exception e) {
            return ResultUtils.error(BusinessCode.OPERATION_ERROR.getCode(), e.getMessage());
        }
    }

    @PostMapping("/heartbeat")
    public BaseResponse<Object> heartbeat(@RequestHeader("Authorization") @NotBlank(message = "Authorization不能为空") String auth,
                                           @RequestBody AgentHeartbeatRequest req) {
        AgentHeartbeatRequestValidator.validate(auth,req);

        String token = auth.replace("Bearer ", "");
        try {
            Boolean flag = executorInfoService.heartbeat(
                    token, req.getHostIp(), req.getExecutorPort());
            return ResultUtils.success(flag);
        } catch (Exception e) {
            return ResultUtils.error(BusinessCode.OPERATION_ERROR.getCode(), e.getMessage());
        }
    }


    /**
     * 批量变更执行器纳管状态，并同步其绑定 Redis 实例
     * @param req
     * @return
     */
    @PostMapping("/management-status")
    public BaseResponse<Integer> updateManagementStatus(@RequestBody ExecutorManagementRequest req) {
        ExecutorManagementRequestValidator.validate(req);
        return ResultUtils.success(
                executorInfoService.updateManagementStatus(req.getExecutorIds(), req.getManagementStatus()));
    }

    /**
     * 执行器到平台的连通性探测接口
     * 执行器在每次采集上报前调用该接口，用 HTTP 往返耗时作为执行器 ping 平台延迟
     */
    @GetMapping("/ping")
    public BaseResponse<Long> ping() {

        return ResultUtils.success(System.currentTimeMillis());
    }
}