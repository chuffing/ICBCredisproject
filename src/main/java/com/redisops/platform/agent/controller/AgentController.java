package com.redisops.platform.agent.controller;

import com.redisops.platform.common.model.ApiResponse;
import com.redisops.platform.agent.model.HeartbeatRequest;
import com.redisops.platform.agent.model.RegisterData;
import com.redisops.platform.agent.model.RegisterRequest;
import com.redisops.platform.agent.entity.ExecutorInfo;
import com.redisops.platform.agent.service.AgentRegistrationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 接收执行器注册和心跳请求。
 */
@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final AgentRegistrationService agentRegistrationService;

    /** 注入执行器注册服务。 */
    public AgentController(AgentRegistrationService agentRegistrationService) {
        this.agentRegistrationService = agentRegistrationService;
    }

    /**
     * 注册执行器并返回 agentId 和 Token。
     *
     * @param request 注册请求
     * @return 注册结果
     */
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<RegisterData>> register(@RequestBody RegisterRequest request) {
        try {
            RegisterData data = agentRegistrationService.register(request);
            return ResponseEntity.ok(ApiResponse.success("注册成功", data));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.failure("REGISTER_CODE_ERROR", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.failure("INVALID_ARGUMENT", e.getMessage()));
        }
    }

    /**
     * 接收执行器心跳，认证通过后刷新在线状态。
     *
     * @param authorization Bearer Token
     * @param request 心跳请求
     * @return 心跳接收结果
     */
    @PostMapping("/heartbeat")
    public ResponseEntity<ApiResponse<Void>> heartbeat(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody HeartbeatRequest request) {
        try {
            agentRegistrationService.heartbeat(authorization, request);
            return ResponseEntity.ok(ApiResponse.success("心跳接收成功", null));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.failure("UNAUTHORIZED", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.failure("INVALID_ARGUMENT", e.getMessage()));
        }
    }

    /**
     * 查询执行器列表，当前用于开发阶段核对注册和心跳结果。
     *
     * @return 执行器列表
     */
    @GetMapping
    public ApiResponse<List<ExecutorInfo>> listExecutors() {
        return ApiResponse.success("查询成功", agentRegistrationService.listExecutors());
    }
}
