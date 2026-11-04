package com.danuvius.platform.controller;

import com.danuvius.platform.common.constant.CommonConstant;
import com.danuvius.platform.common.constant.ReportConstants;
import com.danuvius.platform.common.core.ResultBean;
import com.danuvius.platform.common.exception.ErrorCodeInfo;
import com.danuvius.platform.common.exception.validator.IdValidator;
import com.danuvius.platform.common.exception.validator.StringValidator;
import com.danuvius.platform.model.dto.monitor.*;
import com.danuvius.platform.service.*;
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



    private final RealtimeMetricQueryService realtimeMetricQueryService;
    private final ReportQueryService reportQueryService;
    private final SentinelNodeInfoService sentinelNodeInfoService;
    /* 拓扑与节点状态查询 */
    private final TopologyQueryService topologyQueryService;
    /* 报告导出任务编排 */
    private final ReportExportService reportExportService;

    public MonitorController(RealtimeMetricQueryService realtimeMetricQueryService,
                             ReportQueryService reportQueryService,
                             SentinelNodeInfoService sentinelNodeInfoService,
                             TopologyQueryService topologyQueryService,
                             ReportExportService reportExportService) {
        this.realtimeMetricQueryService = realtimeMetricQueryService;
        this.reportQueryService = reportQueryService;
        this.sentinelNodeInfoService = sentinelNodeInfoService;
        this.topologyQueryService = topologyQueryService;
        this.reportExportService = reportExportService;
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

        return ResultBean.restResult(realtimeMetricQueryService.getInstanceList(managementStatus), ErrorCodeInfo.OK);
    }

    /**
     * 核心实时指标接口
     * @param instanceId
     * @return
     */
    @GetMapping("/realtime/current-values")
    public ResultBean<MetricCurrentValueResponse> currentValues(
            @RequestParam String instanceId) {
        IdValidator.snowflake(instanceId, "instanceId");

        return ResultBean.restResult(realtimeMetricQueryService.getCurrentValues(instanceId), ErrorCodeInfo.OK);
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
        return ResultBean.restResult(realtimeMetricQueryService.getTrend(req), ErrorCodeInfo.OK);
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
        return ResultBean.restResult(topologyQueryService.getTopology(), ErrorCodeInfo.OK);
    }

    /**
     * 主从状态表格接口
     * @return
     */
    @GetMapping("/sentinel/nodes-status")
    public ResultBean<List<NodeStatusResponse>> nodesStatus() {
        return ResultBean.restResult(topologyQueryService.getNodesStatus(), ErrorCodeInfo.OK);
    }

    /**
     * 日周报查询接口
     * @param req
     * @return
     */
    @PostMapping("/report/query")
    public ResultBean<ReportQueryResponse> reportQuery(@RequestBody ReportQueryRequest req) {
        ReportQueryRequestValidator.validate(req);

        return ResultBean.restResult(reportQueryService.queryReport(req), ErrorCodeInfo.OK);
    }


    /**
     * 报告导出接口
     * @param req
     * @return
     */
    @GetMapping("/report/export")
    public ResultBean<ReportExportResponse> reportExport(@RequestBody ReportQueryRequest req) {
        ReportQueryRequestValidator.validate(req);

        return ResultBean.restResult(
                reportExportService.exportReport(req), ErrorCodeInfo.OK);
    }

    /**
     * 导出任务状态查询接口，前端3s轮询
     */
    @GetMapping("/report/export/status")
    public ResultBean<ReportExportResponse> exportStatus(
            @RequestParam String taskId) {
        // 导出任务 id 由 exportReport 生成，格式为 EP_ + 雪花id
        IdValidator.check(taskId, ReportConstants.EXPORT_TASK_ID_PREFIX,
                ReportConstants.EXPORT_TASK_ID_MAX_LEN, "taskId");

        return ResultBean.restResult(reportExportService.queryExportTask(taskId), ErrorCodeInfo.OK);
    }



    /**
     * 导出文件下载接口,任务成功后返回临时文件，30分钟有效
     */
    @GetMapping("/report/export/download")
    public void exportDownload(@RequestParam String taskId, HttpServletResponse response) throws IOException {

        IdValidator.check(taskId, ReportConstants.EXPORT_TASK_ID_PREFIX,
                ReportConstants.EXPORT_TASK_ID_MAX_LEN, "taskId");

        ExportFileDescriptor descriptor = reportExportService.getExportFile(taskId);
        String fileName = URLEncoder.encode(descriptor.getFileName(), StandardCharsets.UTF_8.name())
                .replace("+", "%20");

        response.setContentType(descriptor.getContentType());
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + fileName);

        // Files.copy 内部会打开/关闭输入流，且不会关闭 OutputStream
        Files.copy(descriptor.getFile().toPath(), response.getOutputStream());
    }
}
