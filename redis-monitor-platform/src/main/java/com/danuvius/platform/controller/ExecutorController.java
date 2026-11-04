package com.danuvius.platform.controller;

import com.danuvius.platform.common.core.ResultBean;
import com.danuvius.platform.common.exception.BusinessCode;
import com.danuvius.platform.common.exception.ErrorCodeInfo;
import com.danuvius.platform.model.dto.agent.AgentHeartbeatRequest;
import com.danuvius.platform.model.dto.agent.AgentRegisterRequest;
import com.danuvius.platform.model.dto.agent.AgentRegisterResultDTO;
import com.danuvius.platform.model.dto.executor.ExecutorManagementRequest;
import com.danuvius.platform.service.ExecutorInfoService;
import com.danuvius.platform.util.AuthTokenUtil;
import com.danuvius.platform.util.validate.AgentHeartbeatRequestValidator;
import com.danuvius.platform.util.validate.AgentRegisterRequestValidator;
import com.danuvius.platform.util.validate.ExecutorManagementRequestValidator;
import org.springframework.web.bind.annotation.*;

import javax.validation.constraints.NotBlank;

@RestController
@RequestMapping("/agent")
public class ExecutorController {

    private final ExecutorInfoService executorInfoService;

    public ExecutorController(ExecutorInfoService executorInfoService) {
        this.executorInfoService = executorInfoService;
    }

    @PostMapping("/register")
    public ResultBean<AgentRegisterResultDTO> register( @RequestBody AgentRegisterRequest req) {
        AgentRegisterRequestValidator.validate(req);
        return ResultBean.restResult(executorInfoService.register(req), ErrorCodeInfo.OK);
    }

    /**
     * 执行器心跳上报
     */
    @PostMapping("/heartbeat")
    public ResultBean<Boolean> heartbeat(@RequestHeader("Authorization") String auth,
                                         @RequestBody AgentHeartbeatRequest req) {
        AgentHeartbeatRequestValidator.validate(auth, req);

        String token = AuthTokenUtil.extractBearer(auth);
        return ResultBean.restResult(
                executorInfoService.heartbeat(token, req.getHostIp(), req.getExecutorPort()),
                ErrorCodeInfo.OK);
    }


    /**
     * 批量变更执行器纳管状态，并同步其绑定 Redis 实例
     * @param req
     * @return
     */
    @PostMapping("/management-status")
    public ResultBean<Integer> updateManagementStatus(@RequestBody ExecutorManagementRequest req) {
        ExecutorManagementRequestValidator.validate(req);
        return ResultBean.restResult(
                executorInfoService.updateManagementStatus(req.getExecutorIds(), req.getManagementStatus()), ErrorCodeInfo.OK);
    }

    /**
     * 执行器到平台的连通性探测接口
     * 执行器在每次采集上报前调用该接口，用 HTTP 往返耗时作为执行器 ping 平台延迟
     */
    @GetMapping("/ping")
    public ResultBean<Long> ping() {

        return ResultBean.restResult(System.currentTimeMillis(), ErrorCodeInfo.OK);
    }
}
