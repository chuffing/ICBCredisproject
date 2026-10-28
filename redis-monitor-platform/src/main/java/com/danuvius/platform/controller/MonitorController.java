package com.danuvius.platform.controller;

import com.danuvius.platform.common.constant.CommonConstant;
import com.danuvius.platform.common.core.ResultBean;
import com.danuvius.platform.common.exception.ErrorCodeInfo;
import com.danuvius.platform.common.exception.validator.IdValidator;
import com.danuvius.platform.common.exception.validator.StringValidator;
import com.danuvius.platform.model.dto.monitor.*;
import com.danuvius.platform.service.RedisInstanceService;
import com.danuvius.platform.service.SentinelNodeInfoService;
import com.danuvius.platform.util.validate.ReportQueryRequestValidator;
import com.danuvius.platform.util.validate.TrendRequestValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import javax.validation.constraints.NotBlank;
import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;


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
    public ResultBean<List<RedisInstanceItemDTO>> instances(
            @RequestParam(defaultValue = "1") String managementStatus) {

        StringValidator.isEnumValue(managementStatus, CommonConstant.MANAGEMENT_STATUS, "managementStatus");

        return ResultBean.restResult(redisInstanceService.getInstanceList(managementStatus), ErrorCodeInfo.OK);
    }

    /**
     * 核心实时指标接口
     * @param instanceId
     * @return
     */
    @GetMapping("/realtime/current-values")
    public ResultBean<MetricCurrentValueResponse> currentValues(
            @RequestParam @NotBlank(message = "instanceId不能为空") String instanceId) {
        IdValidator.snowflake(instanceId, "instanceId");

        return ResultBean.restResult(redisInstanceService.getCurrentValues(instanceId), ErrorCodeInfo.OK);
    }

    /**
     * 多节点指标趋势接口
     * @param req
     * @return
     */
    @GetMapping("/realtime/trend")
    public ResultBean<TrendResponse> trend(TrendRequest req) {
        TrendRequestValidator.validate(req);
        log.info("多节点指标趋势接口：{}", req.toString());
        return ResultBean.restResult(redisInstanceService.getTrend(req), ErrorCodeInfo.OK);
    }

    /**
     * Sentinel节点列表接口
     * @param managementStatus
     * @return
     */
    @GetMapping("/sentinel/sentinels")
    public ResultBean<List<SentinelItemResponse>> sentinels(
            @RequestParam(defaultValue = "1") String managementStatus) {
        StringValidator.isEnumValue(managementStatus, CommonConstant.MANAGEMENT_STATUS, "managementStatus");

        return ResultBean.restResult(sentinelNodeInfoService.getSentinelList(managementStatus), ErrorCodeInfo.OK);
    }

    /**
     * 主从拓扑接口
     * @return
     */
    @GetMapping("/sentinel/topology")
    public ResultBean<TopologyResponse> topology() {
        return ResultBean.restResult(redisInstanceService.getTopology(), ErrorCodeInfo.OK);
    }

    /**
     * 主从状态表格接口
     * @return
     */
    @GetMapping("/sentinel/nodes-status")
    public ResultBean<List<NodeStatusResponse>> nodesStatus() {
        return ResultBean.restResult(redisInstanceService.getNodesStatus(), ErrorCodeInfo.OK);
    }

    /**
     * 日周报查询接口
     * @param req
     * @return
     */
    @PostMapping("/report/query")
    public ResultBean<ReportQueryResponse> reportQuery(@RequestBody ReportQueryRequest req) {
        ReportQueryRequestValidator.validate(req);

        return ResultBean.restResult(redisInstanceService.queryReport(req), ErrorCodeInfo.OK);
    }



    /**
     * 报告导出接口
     * @param instanceId
     * @param reportType
     * @return
     */
    @GetMapping("/report/export")
    public ResultBean<ReportExportResponse> reportExport(
            @RequestParam @NotBlank(message = "instanceId不能为空") String instanceId,
            @RequestParam @NotBlank(message = "reportType不能为空") String reportType) {
        IdValidator.snowflake(instanceId, "instanceId");
        StringValidator.isEnumValue(reportType, CommonConstant.REPORT_TYPES, "reportType");

        return ResultBean.restResult(
                redisInstanceService.exportReport(instanceId, reportType), ErrorCodeInfo.OK);
    }

    /**
     * 导出任务状态查询接口，前端3s轮询
     */
    @GetMapping("/report/export/status")
    public ResultBean<ReportExportResponse> exportStatus(
            @RequestParam @NotBlank(message = "taskId不能为空") String taskId) {
        // 导出任务 id 由 exportReport 生成，格式为 EP_ + 雪花id
        IdValidator.check(taskId, "EP_", 32, "taskId");

        return ResultBean.restResult(redisInstanceService.queryExportTask(taskId), ErrorCodeInfo.OK);
    }



    /**
     * 导出文件下载接口,任务成功后返回临时文件，30分钟有效
     */
    @GetMapping("/report/export/download")
    public void exportDownload(
            @RequestParam @NotBlank(message = "taskId不能为空") String taskId,
            HttpServletResponse response) throws IOException {

        IdValidator.check(taskId, "EP_", 32, "taskId");

        File file = redisInstanceService.getExportFile(taskId);
        String fileName = URLEncoder.encode(file.getName(), StandardCharsets.UTF_8.name())
                .replace("+", "%20");

        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + fileName);

        // Files.copy 内部会打开/关闭输入流，且不会关闭 OutputStream
        Files.copy(file.toPath(), response.getOutputStream());
    }
}
