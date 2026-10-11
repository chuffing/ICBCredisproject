package com.redisops.platform.operation.controller;

import com.redisops.platform.common.model.ApiResponse;
import com.redisops.platform.operation.model.CreateOperationTaskRequest;
import com.redisops.platform.operation.model.CreateOperationTaskStepRequest;
import com.redisops.platform.operation.model.OperationTaskDetail;
import com.redisops.platform.operation.service.OperationTaskService;
import com.redisops.platform.operation.type.AgentOperationType;
import com.redisops.platform.operation.type.BusinessTaskType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 创建和查询平台运维任务。
 */
@RestController
@RequestMapping("/api/operation-tasks")
@RequiredArgsConstructor
public class OperationTaskController {

    private final OperationTaskService operationTaskService;

    /**
     * 创建一条业务任务，保存全部步骤并下发第一组步骤。
     *
     * @param request 任务及步骤计划
     * @return 创建后的任务详情
     */
    @PostMapping
    public ResponseEntity<ApiResponse<OperationTaskDetail>> create(
            @RequestBody CreateOperationTaskRequest request) {
        try {
            // 检查入口参数；公共任务规则仍由业务层保证。
            validateCreateRequest(request);
            OperationTaskDetail detail = operationTaskService.createAndDispatch(request);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("任务已创建并开始调度", detail));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.failure("INVALID_ARGUMENT", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.failure("TASK_CONFLICT", e.getMessage()));
        }
    }

    /**
     * 根据 taskNo 查询任务和每台执行器的步骤结果。
     *
     * @param taskNo 业务任务编号
     * @return 任务详情
     */
    @GetMapping("/{taskNo}")
    public ResponseEntity<ApiResponse<OperationTaskDetail>> detail(
            @PathVariable String taskNo) {
        if (!StringUtils.hasText(taskNo)) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.failure("INVALID_ARGUMENT", "taskNo 不能为空"));
        }
        try {
            return ResponseEntity.ok(ApiResponse.success(
                    "查询成功", operationTaskService.getTaskDetail(taskNo)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.failure("TASK_NOT_FOUND", e.getMessage()));
        }
    }

    /** 校验通用任务入口的必填字段、类型及每个步骤的字段。 */
    private void validateCreateRequest(CreateOperationTaskRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("任务内容不能为空");
        }
        BusinessTaskType.fromValue(request.getTaskType());
        if (!StringUtils.hasText(request.getCreatedBy())) {
            throw new IllegalArgumentException("createdBy 不能为空");
        }
        if (!StringUtils.hasText(request.getOperationReason())) {
            throw new IllegalArgumentException("operationReason 不能为空");
        }
        if (request.getSteps() == null || request.getSteps().isEmpty()) {
            throw new IllegalArgumentException("任务至少需要一个执行步骤");
        }
        for (CreateOperationTaskStepRequest step : request.getSteps()) {
            if (step == null || step.getStepNo() == null || step.getStepNo() < 1) {
                throw new IllegalArgumentException("每个 stepNo 都必须大于 0");
            }
            if (!StringUtils.hasText(step.getExecutorId())) {
                throw new IllegalArgumentException("每个步骤都必须指定 executorId");
            }
            AgentOperationType.fromValue(step.getOperationType());
        }
    }
}
