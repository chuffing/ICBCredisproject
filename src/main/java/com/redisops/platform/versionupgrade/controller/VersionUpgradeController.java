package com.redisops.platform.versionupgrade.controller;

import com.redisops.platform.common.model.ApiResponse;
import com.redisops.platform.operation.model.OperationTaskDetail;
import com.redisops.platform.versionupgrade.model.VersionUpgradeNodeRequest;
import com.redisops.platform.versionupgrade.model.VersionUpgradeTaskRequest;
import com.redisops.platform.versionupgrade.service.VersionUpgradeService;
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
 * 创建三节点 Redis + Sentinel 版本升级任务。
 */
@RestController
@RequestMapping("/api/version-upgrade-tasks")
@RequiredArgsConstructor
public class VersionUpgradeController {

    private final VersionUpgradeService versionUpgradeService;

    /** 创建版本升级任务并开始向三台执行器下发版本包。 */
    @PostMapping
    public ResponseEntity<ApiResponse<OperationTaskDetail>> create(
            @RequestBody VersionUpgradeTaskRequest request) {
        try {
            // 先校验请求字段，再查询版本包和执行器并创建任务。
            validateBasicRequest(request);
            OperationTaskDetail detail = versionUpgradeService.createAndDispatch(request);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("版本升级任务已创建并开始调度", detail));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.failure("INVALID_ARGUMENT", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.failure("VERSION_UPGRADE_CONFLICT", e.getMessage()));
        }
    }

    /** 校验任务级字段、三节点数量和 Redis 端口。 */
    private void validateBasicRequest(VersionUpgradeTaskRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("版本升级请求不能为空");
        }
        if (request.getVersionPackageId() == null || request.getVersionPackageId() <= 0) {
            throw new IllegalArgumentException("versionPackageId 必须大于 0");
        }
        if (!StringUtils.hasText(request.getMasterExecutorId())) {
            throw new IllegalArgumentException("masterExecutorId 不能为空");
        }
        if (!StringUtils.hasText(request.getCreatedBy())) {
            throw new IllegalArgumentException("createdBy 不能为空");
        }
        if (!StringUtils.hasText(request.getOperationReason())) {
            throw new IllegalArgumentException("operationReason 不能为空");
        }
        if (request.getNodes() == null || request.getNodes().size() != 3) {
            throw new IllegalArgumentException("版本升级必须且只能包含 3 台服务器");
        }
        if (request.getRedisPort() != null
                && (request.getRedisPort() < 1 || request.getRedisPort() > 65535)) {
            throw new IllegalArgumentException("redisPort 必须在 1 到 65535 之间");
        }

        Set<String> executorIds = new HashSet<>();
        for (VersionUpgradeNodeRequest node : request.getNodes()) {
            if (node == null || !StringUtils.hasText(node.getExecutorId())) {
                throw new IllegalArgumentException("每个节点的 executorId 都不能为空");
            }
            String executorId = node.getExecutorId().trim();
            if (!executorIds.add(executorId)) {
                throw new IllegalArgumentException("节点 executorId 不能重复: " + executorId);
            }
        }
    }
}
