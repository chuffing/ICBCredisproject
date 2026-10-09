package com.redisops.platform.recovery.controller;

import com.redisops.platform.common.model.ApiResponse;
import com.redisops.platform.recovery.model.FaultRecoveryTaskRequest;
import com.redisops.platform.operation.model.OperationTaskDetail;
import com.redisops.platform.recovery.service.FaultRecoveryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 创建三节点 Redis + Sentinel 故障恢复任务。
 */
@RestController
@RequestMapping("/api/fault-recovery-tasks")
public class FaultRecoveryController {

    private final FaultRecoveryService faultRecoveryService;

    /** 注入故障恢复业务服务。 */
    public FaultRecoveryController(FaultRecoveryService faultRecoveryService) {
        this.faultRecoveryService = faultRecoveryService;
    }

    /**
     * 创建故障恢复任务并开始恢复整个 Redis + Sentinel 拓扑。
     *
     * @param request 故障恢复请求
     * @return 创建后的任务及全部步骤
     */
    @PostMapping
    public ResponseEntity<ApiResponse<OperationTaskDetail>> create(
            @RequestBody FaultRecoveryTaskRequest request) {
        try {
            OperationTaskDetail detail = faultRecoveryService.createAndDispatch(request);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("故障恢复任务已创建并开始调度", detail));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.failure("INVALID_ARGUMENT", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.failure("TASK_CONFLICT", e.getMessage()));
        }
    }
}
