package com.redisops.platform.operation.controller;

import lombok.RequiredArgsConstructor;
import com.redisops.platform.common.model.ApiResponse;
import com.redisops.platform.operation.model.CreateOperationTaskRequest;
import com.redisops.platform.operation.model.OperationTaskDetail;
import com.redisops.platform.operation.service.OperationTaskService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
        try {
            return ResponseEntity.ok(ApiResponse.success(
                    "查询成功", operationTaskService.getTaskDetail(taskNo)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.failure("TASK_NOT_FOUND", e.getMessage()));
        }
    }
}
