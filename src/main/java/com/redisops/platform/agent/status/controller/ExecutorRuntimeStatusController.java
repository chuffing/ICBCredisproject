package com.redisops.platform.agent.status.controller;

import lombok.RequiredArgsConstructor;
import com.redisops.platform.agent.status.entity.ExecutorRuntimeStatus;
import com.redisops.platform.agent.status.model.MetricReportRequest;
import com.redisops.platform.agent.status.service.ExecutorRuntimeStatusService;
import com.redisops.platform.common.model.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 接收执行器状态上报，并向页面提供各节点最新状态。 */
@RestController
@RequestMapping("/api/metric")
@RequiredArgsConstructor
public class ExecutorRuntimeStatusController {

    private final ExecutorRuntimeStatusService statusService;

    /** 认证执行器并保存本次Redis和Sentinel采集结果。 */
    @PostMapping("/report")
    public ResponseEntity<ApiResponse<Void>> report(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody MetricReportRequest request) {
        try {
            statusService.receive(authorization, request);
            return ResponseEntity.ok(ApiResponse.success("状态上报已接收", null));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.failure("UNAUTHORIZED", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.failure("INVALID_METRIC", e.getMessage()));
        }
    }

    /** 查询每台执行器最近一次上报的Redis和Sentinel状态。 */
    @GetMapping("/latest")
    public ApiResponse<List<ExecutorRuntimeStatus>> listLatest() {
        return ApiResponse.success("查询成功", statusService.listLatest());
    }
}