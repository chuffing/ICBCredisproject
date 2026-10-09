package com.redisops.platform.operation.controller;

import com.redisops.platform.common.model.ApiResponse;
import com.redisops.platform.operation.model.TaskResultReportRequest;
import com.redisops.platform.operation.service.OperationTaskService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 接收执行器完成原子操作后的结果回调。
 */
@RestController
@RequestMapping("/api/task")
public class TaskReportController {

    private final OperationTaskService operationTaskService;

    /** 注入运维任务调度服务。 */
    public TaskReportController(OperationTaskService operationTaskService) {
        this.operationTaskService = operationTaskService;
    }

    /**
     * 验证执行器身份并保存任务步骤结果。
     *
     * @param authorization 执行器 Bearer Token
     * @param request 步骤结果
     * @return 平台接收结果
     */
    @PostMapping("/report")
    public ResponseEntity<ApiResponse<Void>> report(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody TaskResultReportRequest request) {
        try {
            operationTaskService.receiveResult(authorization, request);
            return ResponseEntity.ok(ApiResponse.success("任务结果已接收", null));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.failure("UNAUTHORIZED", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.failure("INVALID_RESULT", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.failure("STEP_STATUS_CONFLICT", e.getMessage()));
        }
    }
}
