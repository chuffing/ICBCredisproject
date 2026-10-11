package com.redisops.platform.operation.controller;

import lombok.RequiredArgsConstructor;
import com.redisops.platform.common.model.ApiResponse;
import com.redisops.platform.operation.model.TaskResultReportRequest;
import com.redisops.platform.operation.service.OperationTaskService;
import com.redisops.platform.operation.type.AgentOperationType;
import com.redisops.platform.operation.type.StepStatus;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
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
@RequiredArgsConstructor
public class TaskReportController {

    private final OperationTaskService operationTaskService;

    /**
     * 接收执行器上报的步骤结果，调用后端处理并返回对应HTTP响应。
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
            // 校验执行器上报结果的基础参数。
            validateReportRequest(request);

            // 认证执行器，更新任务步骤状态并推动后续调度。
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

    /** 校验回调的必填字段、步骤编号、结果状态及支持的操作类型。 */
    private void validateReportRequest(TaskResultReportRequest request) {
        if (request == null
                || !StringUtils.hasText(request.getTaskNo())
                || request.getStepNo() == null || request.getStepNo() < 1
                || !StringUtils.hasText(request.getAgentId())
                || !StringUtils.hasText(request.getOperationType())) {
            throw new IllegalArgumentException("taskNo、stepNo、agentId 和 operationType 不能为空，stepNo 必须大于 0");
        }
        if (!StepStatus.SUCCESS.name().equals(request.getStatus())
                && !StepStatus.FAILED.name().equals(request.getStatus())) {
            throw new IllegalArgumentException("status 只能是 SUCCESS 或 FAILED");
        }
        AgentOperationType.fromValue(request.getOperationType());
    }
}
