package com.danuvius.platform.controller;

import com.danuvius.platform.common.core.ResultBean;
import com.danuvius.platform.common.exception.BusinessCode;
import com.danuvius.platform.common.exception.ErrorCodeInfo;
import com.danuvius.platform.common.exception.validator.IdValidator;
import com.danuvius.platform.common.exception.validator.StringValidator;
import com.danuvius.platform.model.dto.alarm.record.*;
import com.danuvius.platform.model.dto.alarm.rule.*;
import com.danuvius.platform.service.AlarmRecordService;
import com.danuvius.platform.service.AlarmRuleService;
import com.danuvius.platform.util.validate.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/alarm")
public class AlarmController {

    private final AlarmRecordService alarmRecordService;
    private final AlarmRuleService alarmRuleService;

    public AlarmController(AlarmRecordService alarmRecordService, AlarmRuleService alarmRuleService) {
        this.alarmRecordService = alarmRecordService;
        this.alarmRuleService = alarmRuleService;
    }

    /**
     * 告警规则列表查询接口
     * @param req
     * @return
     */
    @GetMapping("/rule/list")
    public ResultBean<RulePageResponse> getAlarmRuleList( RuleListRequest req) {
        RuleListRequestValidator.validate(req);
        log.info("告警规则列表查询接口：{}", req.toString());

        RulePageResponse rulePageResponse =alarmRuleService.getRulePage(req);
        return ResultBean.restResult(rulePageResponse, ErrorCodeInfo.OK);
    }

    /**
     * 新增告警规则接口
     * @param req
     * @return 返回新增规则id
     */
    @PostMapping("/rule/add")
    public ResultBean<String> addRule( @RequestBody RuleAddRequest req) {
        RuleAddRequestValidator.validate(req);
        log.info("新增告警规则接口：{}", req.toString());

        String ruleId = alarmRuleService.addRule(req);
        return ResultBean.restResult(ruleId, ErrorCodeInfo.OK);
    }

    /**
     * 修改告警规则接口
     * @param req
     * @return
     */
    @PostMapping("/rule/update")
    public ResultBean<Boolean> updateRule( @RequestBody UpdateRequest req) {
        UpdateRequestValidator.validate(req);
        log.info("修改告警规则接口：{}", req.toString());

        Boolean result=alarmRuleService.updateRule(req);
        if(result){
            return ResultBean.restResult(result, ErrorCodeInfo.OK);
        }
        else return ResultBean.resultBean(null,
                BusinessCode.OPERATION_ERROR.getCode(),
                BusinessCode.OPERATION_ERROR.getMsg());
    }

    /**
     * 删除告警规则接口
     * @param ruleId
     * @return
     */
    @PostMapping("/rule/delete")
    public ResultBean<Boolean> deleteRule( @RequestParam(required = false) String ruleId) {
        StringValidator.notBlank(ruleId, "ruleId");
        IdValidator.check(ruleId,"RL_",32,"ruleId");

        Boolean result=alarmRuleService.deleteRule(ruleId);
        if(result){
            return ResultBean.restResult(result, ErrorCodeInfo.OK);
        }
        else return ResultBean.resultBean(null,
                BusinessCode.OPERATION_ERROR.getCode(),
                BusinessCode.OPERATION_ERROR.getMsg());
    }

    /**
     * 告警规则状态切换接口
     * @param req
     * @return
     */
    @PostMapping("/rule/toggle-status")
    public ResultBean<Boolean> toggleStatus( @RequestBody RuleToggleRequest req) {
        RuleToggleRequestValidator.validate(req);
        log.info("告警规则状态切换接口：{}", req.toString());

        Boolean result=alarmRuleService.toggleStatus(req.getRuleId(), req.getEnableStatus());
        if(result){
            return ResultBean.restResult(result, ErrorCodeInfo.OK);
        }
        else return ResultBean.resultBean(null,
                BusinessCode.OPERATION_ERROR.getCode(),
                BusinessCode.OPERATION_ERROR.getMsg());
    }

    /**
     * 单规则检测接口
     * @param ruleId
     * @return
     */
    @PostMapping("/rule/trigger-check")
    public ResultBean<String> triggerCheck( @RequestParam(required = false) String ruleId) {
        StringValidator.notBlank(ruleId, "ruleId");
        IdValidator.check(ruleId,"RL_",32,"ruleId");

        return ResultBean.restResult(alarmRuleService.triggerCheck(ruleId), ErrorCodeInfo.OK);
    }

    /**
     * 全规则检测接口
     * @return
     */
    @PostMapping("/rule/trigger-all-check")
    public ResultBean<String> triggerAllCheck() {

        return ResultBean.restResult(alarmRuleService.triggerAllCheck(), ErrorCodeInfo.OK);
    }

    /**
     * 告警监控页概览统计接口
     * @return
     */
    @GetMapping("/rule/overview")
    public ResultBean<RuleOverviewDTO> ruleOverview() {
        log.info("告警监控页概览统计接口");

        return ResultBean.restResult(alarmRuleService.getRuleOverview(), ErrorCodeInfo.OK);
    }

    /**
     * 未恢复告警列表接口
     * @return
     */
    @GetMapping("/rule/recent-unrecovered")
    public ResultBean<List<RecordItemResponse>> recentUnrecovered() {
        log.info("未恢复告警列表接口");

        return ResultBean.restResult(alarmRecordService.getRecentUnrecovered(), ErrorCodeInfo.OK);
    }

    /**
     * 最近告警列表接口
     * @return
     */
    @GetMapping("/rule/recent")
    public ResultBean<List<RecordItemResponse>> recent() {
        log.info("最近告警列表接口");

        return ResultBean.restResult(alarmRecordService.getRecent(), ErrorCodeInfo.OK);
    }

    /**
     * 告警记录页概览统计接口,最近3天
     * @return
     */
    @GetMapping("/record/overview")
    public ResultBean<RecordOverviewResponse> recordOverview() {
        log.info("告警记录页概览统计接口，最近3天");

        return ResultBean.restResult(alarmRecordService.getRecordOverview(), ErrorCodeInfo.OK);
    }


    /**
     * 告警分页查询接口
     * @param req
     * @return
     */
    @GetMapping("/record/page")
    public ResultBean<RecordPageResponse> recordPage( RecordPageRequest req) {
        RecordPageRequestValidator.validate(req);
        log.info("告警分页查询接口{}", req.toString());

        return ResultBean.restResult(alarmRecordService.getRecordPage(req), ErrorCodeInfo.OK);
    }

    /**
     * 告警记录详情接口
     * @param alarmRecordId(对应表里的id)
     * @return
     */
    @GetMapping("/record/detail")
    public ResultBean<RecordDetailResponse> recordDetail(@RequestParam String alarmRecordId) {
        IdValidator.snowflake(alarmRecordId, "alarmRecordId");
        log.info("告警记录详情接口{}", alarmRecordId);

        return ResultBean.restResult(alarmRecordService.getRecordDetail(alarmRecordId), ErrorCodeInfo.OK);
    }

    /**
     * 告警关联指标波动曲线接口
     * @param alarmRecordId(对应表里的id)
     * @return
     */
    @GetMapping("/record/metric-trend")
    public ResultBean<RecordMetricTrendResponse> metricTrend(@RequestParam  String alarmRecordId) {
        IdValidator.snowflake(alarmRecordId, "alarmRecordId");
        log.info("告警关联指标波动曲线接口{}", alarmRecordId);

        return ResultBean.restResult(alarmRecordService.getMetricTrend(alarmRecordId), ErrorCodeInfo.OK);
    }

    /**
     * 告警人工确认恢复接口
     * @param alarmRecordId(对应表里的id)
     * @return
     */
    @PostMapping("/record/recover")
    public ResultBean<Boolean> recoverRecord( @RequestParam String alarmRecordId) {
        IdValidator.snowflake(alarmRecordId, "alarmRecordId");
        log.info("告警人工确认恢复接口{}", alarmRecordId);

        Boolean result = alarmRecordService.recoverAlarm(alarmRecordId);
        if (result) {
            return ResultBean.restResult(result, ErrorCodeInfo.OK);
        }
        else return ResultBean.resultBean(null,
                BusinessCode.OPERATION_ERROR.getCode(),
                BusinessCode.OPERATION_ERROR.getMsg());
    }

}
