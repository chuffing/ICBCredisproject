package com.redisops.platform.agent.configsnapshot.controller;

import lombok.RequiredArgsConstructor;
import com.redisops.platform.common.model.ApiResponse;
import com.redisops.platform.agent.configsnapshot.model.ConfigSnapshotStatusData;
import com.redisops.platform.agent.configsnapshot.model.ConfigSnapshotUploadRequest;
import com.redisops.platform.agent.configsnapshot.service.ExecutorConfigSnapshotService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 接收执行器基准配置初始化和启动时完整性校验。
 */
@RestController
@RequestMapping("/api/agent/config-snapshot")
@RequiredArgsConstructor
public class AgentConfigSnapshotController {

    private final ExecutorConfigSnapshotService snapshotService;

    /** 查询平台是否已有指定执行器的基准配置。 */
    @GetMapping
    public ResponseEntity<ApiResponse<ConfigSnapshotStatusData>> status(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam("agentId") String agentId) {
        try {
            validateStatusRequest(agentId);
            return ResponseEntity.ok(ApiResponse.success(
                    "配置快照状态查询成功",
                    snapshotService.getStatus(authorization, agentId)));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.failure("UNAUTHORIZED", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.failure("INVALID_ARGUMENT", e.getMessage()));
        }
    }

    /** 首次保存基准配置；已有配置时只校验一致性。 */
    @PostMapping
    public ResponseEntity<ApiResponse<ConfigSnapshotStatusData>> initializeOrVerify(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody ConfigSnapshotUploadRequest request) {
        try {
            validateUploadRequest(request);
            ConfigSnapshotStatusData data = snapshotService.initializeOrVerify(
                    authorization, request);
            return ResponseEntity.ok(ApiResponse.success("配置快照已初始化或校验通过", data));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.failure("UNAUTHORIZED", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.failure("INVALID_ARGUMENT", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.failure("CONFIG_SNAPSHOT_CONFLICT", e.getMessage()));
        }
    }

    /** 校验上传请求中的必填字段。 */
    private void validateUploadRequest(ConfigSnapshotUploadRequest request) {
        if (request == null
                || !StringUtils.hasText(request.getAgentId())
                || !StringUtils.hasText(request.getRedisConfigBase64())
                || !StringUtils.hasText(request.getSentinelConfigBase64())) {
            throw new IllegalArgumentException(
                    "agentId、redisConfigBase64 和 sentinelConfigBase64 不能为空");
        }
    }

    /** 校验配置快照查询中的执行器编号。 */
    private void validateStatusRequest(String executorId) {
        if (!StringUtils.hasText(executorId)) {
            throw new IllegalArgumentException("executorId 不能为空");
        }
    }
}
