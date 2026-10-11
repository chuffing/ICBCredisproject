package com.redisops.platform.serverswitch.controller;

import com.redisops.platform.common.model.ApiResponse;
import com.redisops.platform.operation.model.OperationTaskDetail;
import com.redisops.platform.serverswitch.model.ServerSwitchNodeRequest;
import com.redisops.platform.serverswitch.model.ServerSwitchTaskRequest;
import com.redisops.platform.serverswitch.service.ServerSwitchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashSet;
import java.util.Set;

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
            // 先校验请求字段，再查询版本包和执行器并创建任务。
            validateBasicRequest(request);
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

    /** 校验任务必填字段、节点数量和 Redis 端口。 */
    private void validateBasicRequest(ServerSwitchTaskRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("服务器切换请求不能为空");
        }
        if (request.getVersionPackageId() == null || request.getVersionPackageId() <= 0) {
            throw new IllegalArgumentException("versionPackageId 必须大于 0");
        }
        if (!StringUtils.hasText(request.getMasterHostIp())) {
            throw new IllegalArgumentException("masterHostIp 不能为空");
        }
        if (!StringUtils.hasText(request.getCreatedBy())) {
            throw new IllegalArgumentException("createdBy 不能为空");
        }
        if (!StringUtils.hasText(request.getOperationReason())) {
            throw new IllegalArgumentException("operationReason 不能为空");
        }
        if (request.getNodes() == null || request.getNodes().size() != 3) {
            throw new IllegalArgumentException("服务器切换必须且只能包含 3 台新服务器");
        }
        if (request.getRedisPort() != null
                && (request.getRedisPort() < 1 || request.getRedisPort() > 65535)) {
            throw new IllegalArgumentException("redisPort 必须在 1 到 65535 之间");
        }

        Set<String> hostIps = new HashSet<>();
        for (ServerSwitchNodeRequest node : request.getNodes()) {
            if (node == null || !StringUtils.hasText(node.getHostIp())) {
                throw new IllegalArgumentException("每个节点的 hostIp 都不能为空");
            }
            String hostIp = node.getHostIp().trim();
            if (!hostIps.add(hostIp)) {
                throw new IllegalArgumentException("节点 hostIp 不能重复: " + hostIp);
            }
        }
    }
}
