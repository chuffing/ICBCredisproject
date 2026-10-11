package com.redisops.platform.recovery.controller;

import com.redisops.platform.common.model.ApiResponse;
import com.redisops.platform.recovery.model.FaultRecoveryNodeRequest;
import com.redisops.platform.recovery.model.FaultRecoveryTaskRequest;
import com.redisops.platform.operation.model.OperationTaskDetail;
import com.redisops.platform.recovery.service.FaultRecoveryService;
import lombok.Data;
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
 * 故障恢复controller
 * 负责接收前端传来的故障恢复请求，校验基础参数，并调用业务层创建和调度恢复任务。
 */
@RestController
@RequestMapping("/api/fault-recovery-tasks")
@RequiredArgsConstructor
public class FaultRecoveryController {

    private final FaultRecoveryService faultRecoveryService;

    /**
     * 创建故障恢复任务。
     * 校验请求参数后，调用业务层创建任务并启动异步调度。
     *
     * @param request 故障恢复请求
     * @return 任务创建结果及任务详情
     */
    @PostMapping
    public ResponseEntity<ApiResponse<OperationTaskDetail>> create(
            @RequestBody FaultRecoveryTaskRequest request) {
        try {
            // 校验故障恢复请求的基础参数。
            validateBasicRequest(request);

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

    /**
     * 校验故障恢复请求的基础参数。
     * 检查必填字段、服务器数量、执行器编号校验及Redis端口范围。
     *
     * @param request 故障恢复请求参数
     * @throws IllegalArgumentException 参数不合法时抛出
     */
    private void validateBasicRequest(FaultRecoveryTaskRequest request) {
        if(request == null) {
            throw new IllegalArgumentException("故障恢复请求不能为空");
        }

        if (!StringUtils.hasText(request.getCreatedBy())) {
            throw new IllegalArgumentException("创建人不能为空");
        }

        if (!StringUtils.hasText(request.getOperationReason())) {
            throw new IllegalArgumentException("操作原因不能为空");
        }

        if (!StringUtils.hasText(request.getMasterExecutorId())) {
            throw new IllegalArgumentException("必须指定主节点");
        }

        if(request.getNodes() == null && request.getNodes().size() != 3) {
            throw new IllegalArgumentException("故障恢复必须选择3台服务器");
        }

        // 创建一个空的HashSet集合用来保存已经检查过的执行器编号。
        Set<String> executorIds = new HashSet<>();
        for(FaultRecoveryNodeRequest node : request.getNodes()) {
            if(node == null || !StringUtils.hasText(node.getExecutorId())) {
                throw new IllegalArgumentException("每个节点的执行器编号不能为空");
            }

            // 获取执行器编号，并去除首尾空格。
            String executorId = node.getExecutorId().trim();

            if(!executorIds.add(executorId)) {
                throw new IllegalArgumentException("执行器编号不能重复：" + executorId);
            }
        }

        Integer redisPort = request.getRedisPort();
        if(redisPort != null && (redisPort < 1 || redisPort > 65535)) {
            throw new IllegalArgumentException("Redis端口必须在1到65535之间");
        }
    }
}
