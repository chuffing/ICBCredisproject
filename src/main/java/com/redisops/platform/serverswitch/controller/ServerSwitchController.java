package com.redisops.platform.serverswitch.controller;

import lombok.RequiredArgsConstructor;
import com.redisops.platform.common.model.ApiResponse;
import com.redisops.platform.operation.model.OperationTaskDetail;
import com.redisops.platform.serverswitch.model.ServerSwitchTaskRequest;
import com.redisops.platform.serverswitch.service.ServerSwitchService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 创建三节点 Redis + Sentinel 服务器切换任务。
 */
@RestController
@RequestMapping("/api/server-switch-tasks")
@RequiredArgsConstructor
public class ServerSwitchController {

    private final ServerSwitchService serverSwitchService;

    /** 创建服务器切换任务并开始对三台新服务器执行环境预检。 */
    @PostMapping
    public ResponseEntity<ApiResponse<OperationTaskDetail>> create(
            @RequestBody ServerSwitchTaskRequest request) {
        try {
            OperationTaskDetail detail = serverSwitchService.createAndDispatch(request);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("服务器切换任务已创建并开始调度", detail));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.failure("INVALID_ARGUMENT", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.failure("SERVER_SWITCH_CONFLICT", e.getMessage()));
        }
    }
}
