package com.danuvius.platform.controller;

import com.danuvius.platform.common.BaseResponse;
import com.danuvius.platform.common.ResultUtils;
import com.danuvius.platform.common.constant.CommonConstant;
import com.danuvius.platform.common.exception.validator.IdValidator;
import com.danuvius.platform.common.exception.validator.StringValidator;
import com.danuvius.platform.model.dto.monitor.*;
import com.danuvius.platform.service.RedisInstanceService;
import com.danuvius.platform.service.SentinelNodeInfoService;
import com.danuvius.platform.util.validate.ReportQueryRequestValidator;
import com.danuvius.platform.util.validate.TrendRequestValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.validation.constraints.NotBlank;
import java.util.List;


import javax.servlet.http.HttpServletResponse;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;


@RestController
@RequestMapping("/monitor")
@Slf4j
public class MonitorController {



    private final RedisInstanceService redisInstanceService;
    private final SentinelNodeInfoService sentinelNodeInfoService;

    public MonitorController(RedisInstanceService redisInstanceService, SentinelNodeInfoService sentinelNodeInfoService) {
        this.redisInstanceService = redisInstanceService;
        this.sentinelNodeInfoService = sentinelNodeInfoService;
    }

    /**
     * 查询实例列表接口
     * @param managementStatus
     * @return
     */
    @GetMapping("/redis/instances")
    public BaseResponse<List<RedisInstanceItemDTO>> instances(
            @RequestParam(defaultValue = "1") String managementStatus) {

        StringValidator.isEnumValue(managementStatus, CommonConstant.MANAGEMENT_STATUS, "managementStatus");

        return ResultUtils.success(redisInstanceService.getInstanceList(managementStatus));
    }

    /**
     * 核心实时指标接口
     * @param instanceId
     * @return
     */
    @GetMapping("/realtime/current-values")
    public BaseResponse<MetricCurrentValueResponse> currentValues(
            @RequestParam @NotBlank(message = "instanceId不能为空") String instanceId) {
        IdValidator.snowflake(instanceId, "instanceId");

        return ResultUtils.success(redisInstanceService.getCurrentValues(instanceId));
    }

    /**
     * 多节点指标趋势接口
     * @param req
     * @return
     */
    @GetMapping("/realtime/trend")
    public BaseResponse<TrendResponse> trend(TrendRequest req) {
        TrendRequestValidator.validate(req);

        return ResultUtils.success(redisInstanceService.getTrend(req));
    }

    /**
     * Sentinel节点列表接口
     * @param managementStatus
     * @return
     */
    @GetMapping("/sentinel/sentinels")
    public BaseResponse<List<SentinelItemResponse>> sentinels(
            @RequestParam(defaultValue = "1") String managementStatus) {
        StringValidator.isEnumValue(managementStatus, CommonConstant.MANAGEMENT_STATUS, "managementStatus");

        return ResultUtils.success(sentinelNodeInfoService.getSentinelList(managementStatus));
    }

    /**
     * 主从拓扑接口
     * @return
     */
    @GetMapping("/sentinel/topology")
    public BaseResponse<TopologyResponse> topology() {
        return ResultUtils.success(redisInstanceService.getTopology());
    }

    /**
     * 主从状态表格接口
     * @return
     */
    @GetMapping("/sentinel/nodes-status")
    public BaseResponse<List<NodeStatusResponse>> nodesStatus() {
        return ResultUtils.success(redisInstanceService.getNodesStatus());
    }

    /**
     * 日周报查询接口
     * @param req
     * @return
     */
    @PostMapping("/report/query")
    public BaseResponse<ReportQueryResponse> reportQuery(@RequestBody ReportQueryRequest req) {
        ReportQueryRequestValidator.validate(req);

        return ResultUtils.success(redisInstanceService.queryReport(req));
    }



    /**
     * 报告导出接口
     * @param instanceId
     * @param reportType
     * @return
     */
    @GetMapping("/report/export")
    public BaseResponse<ReportExportResponse> reportExport(
            @RequestParam @NotBlank(message = "instanceId不能为空") String instanceId,
            @RequestParam @NotBlank(message = "reportType不能为空") String reportType) {
        IdValidator.snowflake(instanceId, "instanceId");
        StringValidator.isEnumValue(reportType, CommonConstant.REPORT_TYPES, "reportType");

        return ResultUtils.success(
                redisInstanceService.exportReport(instanceId, reportType));
    }

    /**
     * 导出任务状态查询接口，前端3s轮询
     */
    @GetMapping("/report/export/status")
    public BaseResponse<ReportExportResponse> exportStatus(
            @RequestParam @NotBlank(message = "taskId不能为空") String taskId) {
        IdValidator.check(taskId, "taskId",32,"taskId");

        return ResultUtils.success(redisInstanceService.queryExportTask(taskId));
    }



    /**
     * 导出文件下载接口,任务成功后返回临时文件，30分钟有效
     */
    @GetMapping("/report/export/download")
    public void exportDownload(
            @RequestParam @NotBlank(message = "taskId不能为空") String taskId,
            HttpServletResponse response) throws IOException {

        IdValidator.check(taskId, "taskId", 32, "taskId");

        File file = redisInstanceService.getExportFile(taskId);
        String fileName = URLEncoder.encode(file.getName(), StandardCharsets.UTF_8.name())
                .replace("+", "%20");

        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + fileName);

        // Files.copy 内部会打开/关闭输入流，且不会关闭 OutputStream
        Files.copy(file.toPath(), response.getOutputStream());
    }
}