package com.redisops.platform.versionupgrade.controller;

import com.redisops.platform.common.model.ApiResponse;
import com.redisops.platform.operation.model.OperationTaskDetail;
import com.redisops.platform.versionupgrade.model.VersionUpgradeTaskRequest;
import com.redisops.platform.versionupgrade.service.VersionUpgradeService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 创建三节点 Redis + Sentinel 版本升级任务。
 */
@RestController
@RequestMapping("/api/version-upgrade-tasks")
public class VersionUpgradeController {

    private final VersionUpgradeService versionUpgradeService;

    /** 注入版本升级业务服务。 */
    public VersionUpgradeController(VersionUpgradeService versionUpgradeService) {
        this.versionUpgradeService = versionUpgradeService;
    }

    /** 创建版本升级任务并开始向三台执行器下发版本包。 */
    @PostMapping
    public ResponseEntity<ApiResponse<OperationTaskDetail>> create(
            @RequestBody VersionUpgradeTaskRequest request) {
        try {
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
}
