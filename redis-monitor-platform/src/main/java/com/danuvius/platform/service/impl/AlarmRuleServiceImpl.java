package com.danuvius.platform.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.danuvius.platform.common.constant.CommonConstant;
import com.danuvius.platform.common.core.ResultBean;
import com.danuvius.platform.common.exception.BusinessCode;
import com.danuvius.platform.common.exception.BusinessException;
import com.danuvius.platform.mapper.*;
import com.danuvius.platform.model.dto.alarm.rule.*;
import com.danuvius.platform.model.dto.metric.MetricReportItemDTO;
import com.danuvius.platform.model.entity.*;
import com.danuvius.platform.model.enums.metric.MetricTypeEnum;
import com.danuvius.platform.model.enums.StatusEnum;
import com.danuvius.platform.model.enums.rule.EnableStatusEnum;
import com.danuvius.platform.service.*;
import com.danuvius.platform.util.CompareUtil;
import com.danuvius.platform.util.DetectTaskRecorder;
import com.danuvius.platform.util.ParseUtils;
import com.danuvius.platform.util.RuleMatchHelper;
import com.danuvius.platform.util.http.AgentHttpClient;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AlarmRuleServiceImpl extends ServiceImpl<AlarmRuleMapper, AlarmRule>
        implements AlarmRuleService {
    private final AlarmRuleMapper alarmRuleMapper;
    private final AlarmRecordMapper alarmRecordMapper;
    private final RedisInstanceMapper redisInstanceMapper;
    private final ExecutorInfoMapper executorInfoMapper;
    private final MetricLogMapper metricLogMapper;
    private final MetricLogService metricLogService;
    private final RuleMatchHelper ruleMatchHelper;
    private final AgentHttpClient agentHttpClient;
    /*告警邮件通知*/
    private final AlarmNoticeService alarmNoticeService;
    /*操作审计日志*/
    private final AuditLogService auditLogService;
    /*告警检测任务状态记录*/
    private final DetectTaskRecorder detectTaskRecorder;
    /*通用异步任务执行器（独立Bean保证@Async生效）*/
    private final AsyncTaskRunner asyncTaskRunner;

    /** 持续时长默认值，周期 */
    private static final int DEFAULT_DURATION_MIN = 1;
    /** 重复告警抑制默认值，s */
    private static final int DEFAULT_SILENCE_MIN = 1800;


    private static final String REPL_DELAY_METRIC_KEY = "replDelay";

    /*手动检测统一为异步提交，固定返回文案*/
    private static final String RESULT_HAS_ALARM_CHECK =
            "已触发手动检测，请稍后在告警记录中查看结果";
    private static final String RESULT_HAS_ALARM_ALL =
            "已触发全量检测，请稍后在告警记录中查看结果";

    // 自动恢复：指标连续 N 个采集周期回落阈值以下自动恢复，默认 2；<=0 关闭
    @Value("${platform.alarm.auto-recover.periods:2}")
    private int autoRecoverPeriods;


    public AlarmRuleServiceImpl(AlarmRuleMapper alarmRuleMapper,
                                AlarmRecordMapper alarmRecordMapper,
                                RedisInstanceMapper redisInstanceMapper,
                                ExecutorInfoMapper executorInfoMapper,
                                MetricLogMapper metricLogMapper,
                                MetricLogService metricLogService,
                                RuleMatchHelper ruleMatchHelper,
                                AgentHttpClient agentHttpClient,
                                AlarmNoticeService alarmNoticeService,
                                AuditLogService auditLogService,
                                DetectTaskRecorder detectTaskRecorder,
                                AsyncTaskRunner asyncTaskRunner) {
        this.alarmRuleMapper = alarmRuleMapper;
        this.alarmRecordMapper = alarmRecordMapper;
        this.redisInstanceMapper = redisInstanceMapper;
        this.executorInfoMapper = executorInfoMapper;
        this.metricLogMapper = metricLogMapper;
        this.metricLogService = metricLogService;
        this.ruleMatchHelper = ruleMatchHelper;
        this.agentHttpClient = agentHttpClient;
        this.alarmNoticeService = alarmNoticeService;
        this.auditLogService = auditLogService;
        this.detectTaskRecorder = detectTaskRecorder;
        this.asyncTaskRunner = asyncTaskRunner;
    }

    /**
     * 规则列表分页
     * @param req
     * @return
     */
    @Override
    public RulePageResponse getRulePage(RuleListRequest req) {
        int pageNum = req.getPageNum();
        int pageSize = req.getPageSize();

        String keyword = req.getKeyword();
        String alarmLevel=req.getAlarmLevel();
        List<String> scopeInstanceIds=req.getScopeInstanceIds();
        String enableStatus=req.getEnableStatus();
        log.info("alarmLevel:"+alarmLevel);

        Page<AlarmRule> page = page(new Page<>(pageNum, pageSize), Wrappers.<AlarmRule>lambdaQuery()
                .eq(StringUtils.isNotBlank(alarmLevel),AlarmRule::getAlarmLevel, alarmLevel)
                .eq(StringUtils.isNotBlank(enableStatus),AlarmRule::getEnableStatus, enableStatus)
                .and(StringUtils.isNotBlank(keyword), i->
                        i.like(AlarmRule::getRuleName, keyword)
                                .or()
                                .like(AlarmRule::getMetricName, keyword)
                )

                .in(CollectionUtils.isNotEmpty(scopeInstanceIds),AlarmRule::getScopeInstanceId, scopeInstanceIds)
                .orderByDesc(AlarmRule::getCreatedTime)
        );

        RulePageResponse resp = new RulePageResponse();

        /*回显生效实例名称展示数组 scopeInstanceNames */
        resp.setScopeInstanceNames(fillScopeInstanceNames(scopeInstanceIds));
        resp.setPage(page);
        return resp;
    }


    /**
     * 将 scopeInstanceIds 转换为对应的 instanceName 列表,找不到的 ID 保留原值
     */
    private List<String> fillScopeInstanceNames(List<String> scopeInstanceIds) {
        if (CollectionUtils.isEmpty(scopeInstanceIds)) {
            return Collections.emptyList();
        }

        //根据scopeInstanceId查表
        LambdaQueryWrapper<RedisInstance> wrapper = Wrappers.<RedisInstance>lambdaQuery()
                .select(RedisInstance::getInstanceId,RedisInstance::getInstanceName)
                .in(RedisInstance::getInstanceId, scopeInstanceIds);

        List<RedisInstance> list = redisInstanceMapper.selectList(wrapper);

        //id name对应
        Map<String, String> idNameMap = new HashMap<>();
        for (RedisInstance instance : list) {
            idNameMap.put(instance.getInstanceId(), instance.getInstanceName());
        }

        //按入参顺序一一映射
        List<String> instanceNames = new ArrayList<>(scopeInstanceIds.size());
        for (String id : scopeInstanceIds) {
            instanceNames.add(idNameMap.getOrDefault(id, id));
        }
        return instanceNames;

    }

    /**
     * 加规则
     * @param req
     * @return 新增规则id
     */
    @Override
    public String addRule(RuleAddRequest req) {
        // 规则治理校验：按指标维度拆分、同指标多级阈值、同范围同级别唯一、数量上限
        MetricTypeEnum metricType = validateRuleGovernance(req.getMetricKey(), req.getCompareType(),
                req.getThreshold(), req.getAlarmLevel(), req.getScopeType(),
                req.getScopeInstanceIds(), null);

        AlarmRule rule = new AlarmRule();
        rule.setRuleId("RL_" + IdUtil.getSnowflakeNextIdStr());
        rule.setRuleName(req.getRuleName().trim());
        rule.setMetricKey(req.getMetricKey());
        // 指标名称/单位一律由后端按指标枚举派生，避免前端传入跨指标单位
        rule.setMetricName(metricType.getName());
        rule.setUnit(metricType.getUnit());
        rule.setCompareType(req.getCompareType());

        //Double转String
        rule.setThreshold(ParseUtils.doubleToString(req.getThreshold()));

        rule.setCollectInterval(req.getCollectInterval());
        rule.setDuration(req.getDuration());
        rule.setAlarmLevel(req.getAlarmLevel());
        rule.setSilenceDuration(req.getSilenceDuration());
        rule.setScopeType(req.getScopeType());
        rule.setScopeInstanceId(ParseUtils.joinIds(normalizeInstanceIds(req.getScopeInstanceIds())));
        rule.setEnableStatus(req.getEnableStatus());

        rule.setDelFlag(CommonConstant.NOT_DELETED);

        rule.setCreatedBy(req.getCreatedBy());
        rule.setCreatedTime(LocalDateTime.now());
        rule.setUpdatedTime(LocalDateTime.now());
        alarmRuleMapper.insert(rule);
        /* 将操作人、规则id、规则名、操作类型、操作时间记入操作审计日志 */
        auditLogService.record(CommonConstant.MODULE_ALARM_RULE, CommonConstant.OPERATION_TYPE_ADD, rule.getRuleId(),
                "新增告警规则：" + req.getRuleName());
        return rule.getRuleId();
    }


    /**
     * 告警规则治理校验（新增/修改共用）
     *
     * 设计原则：多规则定义 + 抑制/分组治理
     * 1. 一条规则只绑定一个指标维度，指标必须属于可量化的规则指标；
     * 2. 同一指标 + 同一生效范围允许“提示/警告/严重”多级阈值，各级别只能有一条；
     * 3. 同一指标 + 同一生效范围的比较方式必须一致，阈值随级别单调递增（> / >=）
     *    或单调递减（< / <=），避免多级规则互相矛盾；
     * 4. = 只允许单条规则；同一阈值重复配置直接拒绝；
     * 5. 同一指标启用规则总数受 CommonConstant类  MAX_RULES_PER_METRIC 限制，
     *    避免为同一指标叠加过多“细节规则”
     *
     * @param metricKey        监控指标
     * @param compareType      比较方式
     * @param thresholdValue   告警阈值
     * @param alarmLevel       告警级别
     * @param scopeType        生效范围：1 全部节点 2 指定节点
     * @param scopeInstanceIds 生效节点列表
     * @param excludeRuleId    修改场景排除自身；新增传 null
     * @return 指标枚举
     */
    private MetricTypeEnum validateRuleGovernance(String metricKey, String compareType, Double thresholdValue,
                                                  String alarmLevel, String scopeType,
                                                  List<String> scopeInstanceIds, String excludeRuleId) {
        MetricTypeEnum metricType = MetricTypeEnum.ruleOf(metricKey);
        if (metricType == null) {
            throw new BusinessException(BusinessCode.PARAMS_ERROR.getCode(),
                    "不支持的监控指标：" + metricKey + "（节点存活/哨兵状态属于系统事件，不开放阈值规则）");
        }
        if (thresholdValue == null) {
            throw new BusinessException(BusinessCode.PARAMS_ERROR.getCode(), "告警阈值不能为空");
        }
        int newLevel;
        try {
            newLevel = Integer.parseInt(alarmLevel);
        } catch (Exception e) {
            throw new BusinessException(BusinessCode.PARAMS_ERROR.getCode(), "告警级别非法：" + alarmLevel);
        }

        List<AlarmRule> sameMetricRules = alarmRuleMapper.selectList(Wrappers.<AlarmRule>lambdaQuery()
                .eq(AlarmRule::getEnableStatus, CommonConstant.ENABLE)
                .eq(AlarmRule::getMetricKey, metricKey)
        );

        String newScopeSignature = scopeSignature(scopeType, scopeInstanceIds);
        List<AlarmRule> sameScopeRules = new ArrayList<>();
        int activeCount = 0;
        for (AlarmRule exist : sameMetricRules) {
            if (excludeRuleId != null && excludeRuleId.equals(exist.getRuleId())) {
                continue;
            }
            activeCount++;
            String existSignature = scopeSignature(exist.getScopeType(),
                    splitInstanceIds(exist.getScopeInstanceId()));
            if (newScopeSignature.equals(existSignature)) {
                sameScopeRules.add(exist);
            }
        }

        // 同一指标启用规则总数上限
        if (activeCount >= CommonConstant.MAX_RULES_PER_METRIC) {
            throw new BusinessException(BusinessCode.OPERATION_ERROR.getCode(),
                    "指标【" + metricType.getName() + "】的启用规则已达上限 "
                            + CommonConstant.MAX_RULES_PER_METRIC + " 条，请先清理冗余规则");
        }
        // 7. 同一指标 + 同一生效范围最多 3 个级别
        if (sameScopeRules.size() >= CommonConstant.MAX_RULE_LEVELS_PER_SCOPE) {
            throw new BusinessException(BusinessCode.OPERATION_ERROR.getCode(),
                    "指标【" + metricType.getName() + "】在该生效范围内已达到 "
                            + CommonConstant.MAX_RULE_LEVELS_PER_SCOPE + " 级阈值上限");
        }

        for (AlarmRule exist : sameScopeRules) {
            if (alarmLevel.equals(exist.getAlarmLevel())) {
                throw new BusinessException(BusinessCode.OPERATION_ERROR.getCode(),
                        "指标【" + metricType.getName() + "】在该生效范围内已存在"
                                + levelDesc(alarmLevel) + "级规则，请直接修改原规则");
            }
            if (!compareType.equals(exist.getCompareType())) {
                throw new BusinessException(BusinessCode.OPERATION_ERROR.getCode(),
                        "指标【" + metricType.getName() + "】在同一生效范围内的比较方式必须一致（已存在 "
                                + exist.getCompareType() + " 规则）");
            }
            Double existThreshold = ParseUtils.stringToDouble(exist.getThreshold());
            if (existThreshold != null
                    && Double.compare(existThreshold, thresholdValue) == 0) {
                throw new BusinessException(BusinessCode.OPERATION_ERROR.getCode(),
                        "指标【" + metricType.getName() + "】在该生效范围内已存在相同阈值 "
                                + exist.getThreshold() + " 的规则");
            }
        }

        // = 不支持多级阈值
        if ("=".equals(compareType) && !sameScopeRules.isEmpty()) {
            throw new BusinessException(BusinessCode.OPERATION_ERROR.getCode(),
                    "指标【" + metricType.getName() + "】在同一生效范围内使用“=”时只能配置一条规则");
        }

        // 多级阈值单调性：级别越高阈值越严格，避免多级规则互相矛盾
        if (!"=".equals(compareType)) {
            boolean greaterDirection = ">".equals(compareType) || ">=".equals(compareType);
            for (AlarmRule exist : sameScopeRules) {
                Double existThreshold = ParseUtils.stringToDouble(exist.getThreshold());
                Integer existLevel = ParseUtils.stringToInteger(exist.getAlarmLevel());
                if (existThreshold == null || existLevel == null) {
                    continue;
                }
                if (newLevel > existLevel) {
                    boolean invalid = greaterDirection
                            ? thresholdValue <= existThreshold
                            : thresholdValue >= existThreshold;
                    if (invalid) {
                        throw new BusinessException(BusinessCode.OPERATION_ERROR.getCode(),
                                "多级阈值不合理：" + levelDesc(alarmLevel) + "级阈值必须"
                                        + (greaterDirection ? "高于" : "低于")
                                        + levelDesc(exist.getAlarmLevel()) + "级阈值 " + exist.getThreshold());
                    }
                } else if (newLevel < existLevel) {
                    boolean invalid = greaterDirection
                            ? thresholdValue >= existThreshold
                            : thresholdValue <= existThreshold;
                    if (invalid) {
                        throw new BusinessException(BusinessCode.OPERATION_ERROR.getCode(),
                                "多级阈值不合理：" + levelDesc(alarmLevel) + "级阈值必须"
                                        + (greaterDirection ? "低于" : "高于")
                                        + levelDesc(exist.getAlarmLevel()) + "级阈值 " + exist.getThreshold());
                    }
                }
            }
        }
        return metricType;
    }

    /** 生效范围签名：全部节点=ALL；指定节点=排序去重后的实例id集合 */
    private String scopeSignature(String scopeType, List<String> instanceIds) {
        if (CommonConstant.ALL_INSTANCE_IDS.equals(scopeType)) {
            return "ALL";
        }
        java.util.TreeSet<String> sorted = new java.util.TreeSet<>();
        if (instanceIds != null) {
            for (String id : instanceIds) {
                if (id != null && !id.trim().isEmpty()) {
                    sorted.add(id.trim());
                }
            }
        }
        return "IDS:" + String.join(",", sorted);
    }

    private List<String> splitInstanceIds(String joined) {
        List<String> ids = new ArrayList<>();
        if (joined == null || joined.trim().isEmpty()) {
            return ids;
        }
        for (String id : joined.split(",")) {
            String trimmed = id.trim();
            if (!trimmed.isEmpty()) {
                ids.add(trimmed);
            }
        }
        return ids;
    }

    /** 实例id去重、去空白，保持前端顺序 */
    private List<String> normalizeInstanceIds(List<String> ids) {
        java.util.LinkedHashSet<String> unique = new java.util.LinkedHashSet<>();
        if (ids != null) {
            for (String id : ids) {
                if (id != null && !id.trim().isEmpty()) {
                    unique.add(id.trim());
                }
            }
        }
        return new ArrayList<>(unique);
    }

    private String levelDesc(String level) {
        switch (level) {
            case "1":
                return "提示";
            case "2":
                return "警告";
            case "3":
                return "严重";
            default:
                return level;
        }
    }



    /**
     * 改规则
     * @param req
     * @return
     */
    @Override
    public boolean updateRule(UpdateRequest req) {
        AlarmRule rule = alarmRuleMapper.selectOne(Wrappers.<AlarmRule>lambdaQuery()
                .eq(AlarmRule::getRuleId, req.getRuleId()));

        if (rule == null) throw new BusinessException(BusinessCode.NOT_FOUND_ERROR.getCode(), "规则不存在");
        /* 修改时同样做治理校验（排除自身） */
        MetricTypeEnum metricType = validateRuleGovernance(req.getMetricKey(), req.getCompareType(),
                req.getThreshold(), req.getAlarmLevel(), req.getScopeType(),
                req.getScopeInstanceIds(), rule.getRuleId());

        rule.setRuleName(req.getRuleName().trim());
        rule.setMetricKey(req.getMetricKey());
        // 指标名称与单位由后端派生，忽略前端传入值
        rule.setMetricName(metricType.getName());
        rule.setUnit(metricType.getUnit());
        rule.setCompareType(req.getCompareType());
        rule.setThreshold(ParseUtils.doubleToString(req.getThreshold()));
        rule.setCollectInterval(req.getCollectInterval());
        rule.setDuration(req.getDuration());
        rule.setAlarmLevel(req.getAlarmLevel());
        rule.setSilenceDuration(req.getSilenceDuration());
        rule.setScopeType(req.getScopeType());
        rule.setScopeInstanceId(ParseUtils.joinIds(normalizeInstanceIds(req.getScopeInstanceIds())));
        rule.setEnableStatus(req.getEnableStatus());
        rule.setUpdatedTime(LocalDateTime.now());

        int rows= alarmRuleMapper.updateById(rule);
        if (rows > 0) {
            /* 操作审计日志记录 */
            auditLogService.record(CommonConstant.MODULE_ALARM_RULE, CommonConstant.OPERATION_TYPE_UPDATE, rule.getRuleId(),
                    "修改告警规则：" + req.getRuleName());
        }
        return rows > 0;


    }

    /**
     * 删规则
     * @param ruleId
     * @return
     */
    @Override
    public boolean deleteRule(String ruleId) {
        AlarmRule rule = alarmRuleMapper.selectOne(Wrappers.<AlarmRule>lambdaQuery()
                .eq(AlarmRule::getRuleId, ruleId));

        if (rule == null) throw new BusinessException(BusinessCode.NOT_FOUND_ERROR.getCode(), "规则不存在");
        int rows = alarmRuleMapper.update(null,
                new LambdaUpdateWrapper<AlarmRule>()
                        .set(AlarmRule::getDelFlag, EnableStatusEnum.ENABLE.getCode())
                        .set(AlarmRule::getUpdatedTime, LocalDateTime.now())
                        .eq(AlarmRule::getRuleId, ruleId)
        );
        if (rows > 0) {
            /* 操作审计日志记录 */
            auditLogService.record(CommonConstant.MODULE_ALARM_RULE, CommonConstant.OPERATION_TYPE_DELETE, ruleId,
                    "删除告警规则：" + rule.getRuleName());
        }
        if (rows == 0) {
            // 并发下已被删除
            log.warn("删除告警规则时记录已被并发删除, ruleId={}", ruleId);
            return false;
        }
        return rows > 0;
    }

    /**
     * 改规则启用
     * @param ruleId
     * @param enableStatus
     * @return
     */
    @Override
    public boolean toggleStatus(String ruleId, String enableStatus) {
        AlarmRule rule = alarmRuleMapper.selectOne(Wrappers.<AlarmRule>lambdaQuery()
                .eq(AlarmRule::getRuleId, ruleId));

        if (rule == null) throw new BusinessException(BusinessCode.NOT_FOUND_ERROR.getCode(), "规则不存在");

        int rows= alarmRuleMapper.update(null,new LambdaUpdateWrapper<AlarmRule>()
                .set(AlarmRule::getEnableStatus, enableStatus)
                .set(AlarmRule::getUpdatedTime, LocalDateTime.now())
                .eq(AlarmRule::getRuleId, ruleId)
        );
        if (rows > 0) {
            /* 操作审计日志记录 */
            auditLogService.record(CommonConstant.MODULE_ALARM_RULE, CommonConstant.OPERATION_TYPE_UPDATE, ruleId,
                    "切换告警规则启用状态：" + rule.getRuleName() + " -> "
                            + ("1".equals(enableStatus) ? CommonConstant.ENABLE_DEC : CommonConstant.DISABLE_DEC));
        }
        return rows > 0;
    }



    /**
     * 单规则手动检测，异步提交：同步仅做规则校验并返回固定提示，
     * 检测任务异步执行，结果写入告警记录，不在响应中暴露检测结果
     *     1) 校验规则存在且启用
     *     2) 异步触发相关执行器采集，写 rms_metric_log
     *     3) 用采集数据判断该规则是否命中，命中写 alarm_record 并邮件通知
     * @param ruleId
     * @return
     */
    @Override
    public String triggerCheck(String ruleId) {
        AlarmRule rule = alarmRuleMapper.selectOne(Wrappers.<AlarmRule>lambdaQuery()
                .eq(AlarmRule::getRuleId, ruleId));

        if (rule == null) {
            throw new BusinessException(BusinessCode.NOT_FOUND_ERROR.getCode(), "规则不存在");
        }
        if (!CommonConstant.ENABLE.equals(rule.getEnableStatus())) {
            throw new BusinessException(BusinessCode.OPERATION_ERROR.getCode(), "规则禁用，无法检测");
        }
        asyncTaskRunner.run(() -> {
            try {
                executeSingleCheck(ruleId);
                detectTaskRecorder.record("single-rule", true);
            } catch (Exception e) {
                log.error("[手动检测] 单规则检测任务执行失败, ruleId={}", ruleId, e);
                detectTaskRecorder.record("single-rule", false);
            }
        });
        return RESULT_HAS_ALARM_CHECK;
    }

    /**
     * 全规则手动检测，异步提交，同步返回固定提示
     * @return
     */
    @Override
    public String triggerAllCheck() {
        asyncTaskRunner.run(() -> {
            try {
                executeAllCheck();
                detectTaskRecorder.record("all-rule", true);
            } catch (Exception e) {
                log.error("[手动检测] 全量检测任务执行失败", e);
                detectTaskRecorder.record("all-rule", false);
            }
        });
        return RESULT_HAS_ALARM_ALL;
    }

    /**
     * 单规则检测执行体（由 AlarmCheckExecutor 异步调用）
     */
    @Override
    public void executeSingleCheck(String ruleId) {
        AlarmRule rule = alarmRuleMapper.selectOne(Wrappers.<AlarmRule>lambdaQuery()
                .eq(AlarmRule::getRuleId, ruleId));


        //找不到rule 或者规则禁用
        if (rule == null || CommonConstant.DISABLE.equals(rule.getEnableStatus())) {
            return;
        }
        //解析规则作用范围的实例列表
        List<String> instanceIds = ruleMatchHelper.resolveRuleInstanceIds(rule);
        //触发相关执行器采集，写 rms_metric_log
        List<MetricReportItemDTO> collected = collectByInstanceIds(instanceIds);
        //用采集数据判断该规则
        checkAndGenerateAlarms(collected, Collections.singletonList(rule));
        //本次检测后，对该规则下的未恢复告警做自动恢复检查
        checkAndAutoRecover(Collections.singletonList(rule));
    }

    /**
     * 全规则检测执行体（由 AlarmCheckExecutor 异步调用）
     */
    @Override
    public void executeAllCheck() {
        List<AlarmRule> rules= alarmRuleMapper.selectList(Wrappers.<AlarmRule>lambdaQuery()
                .eq(AlarmRule::getEnableStatus, CommonConstant.ENABLE)
        );

        if (rules.isEmpty()) {
            return;
        }
        List<MetricReportItemDTO> collected = collectFromAllExecutors();
        checkAndGenerateAlarms(collected, rules);
        //本次全量检测后，对全部启用规则下的未恢复告警做自动恢复检查
        checkAndAutoRecover(rules);
    }


    /** rms_metric_log -> 规则判定用快照 */
    private MetricReportItemDTO toReportItem(MetricLog metricLog, RedisInstance instance) {
        MetricReportItemDTO item = new MetricReportItemDTO();
        item.setInstanceId(metricLog.getInstanceId());
        item.setCpuPer(metricLog.getCpuPer());
        item.setMemServerPer(metricLog.getMemServerPer());
        item.setMemUs(metricLog.getMemUs());
        item.setLatency(metricLog.getLatency());
        item.setPingLatency(metricLog.getPingLatency());
        item.setIoIik(metricLog.getIoIik());
        item.setIoIok(metricLog.getIoIok());
        item.setOps(metricLog.getOps());
        item.setReplDelay(metricLog.getReplDelay());
        item.setRole(metricLog.getRole() != null ? metricLog.getRole() : instance.getRole());
        item.setRedisVersion(metricLog.getRedisVersion() != null
                ? metricLog.getRedisVersion() : instance.getRedisVersion());
        item.setRunId(metricLog.getRunId());
        item.setMasterHost(metricLog.getMasterHost());
        item.setMasterPort(metricLog.getMasterPort());
        item.setCollectType(CommonConstant.AUTO);
        return item;
    }

    @Override
    public RuleOverviewDTO getRuleOverview() {
        RuleOverviewDTO dto = alarmRuleMapper.selectRuleOverview();
        if (dto == null) {
            dto = new RuleOverviewDTO();
        }
        /* 今日告警分级统计（由SQL扁平字段组装） */
        RuleOverviewDTO.TodayAlarmLevelCount levelCount = new RuleOverviewDTO.TodayAlarmLevelCount();
        levelCount.setInfo(dto.getTodayInfoCount() == null ? 0L : dto.getTodayInfoCount());
        levelCount.setAlarm(dto.getTodayAlarmCount() == null ? 0L : dto.getTodayAlarmCount());
        levelCount.setSeverity(dto.getTodaySeverityCount() == null ? 0L : dto.getTodaySeverityCount());
        dto.setTodayAlarmLevelCount(levelCount);
        /* 告警检测任务状态 */
        dto.setDetectTaskStatus(detectTaskRecorder.currentStatus());
        log.info("规则概览返回-告警检测任务状态：{}",dto.getDetectTaskStatus());
        return dto;
    }



    /**
     * 触发指定实例对应的执行器采集，并将返回数据写入 rms_metric_log
     * 同一执行器只触发一次
     */
    private List<MetricReportItemDTO> collectByInstanceIds(List<String> instanceIds) {
        List<MetricReportItemDTO> collected = new ArrayList<>();
        if (instanceIds == null || instanceIds.isEmpty()) {
            return collected;
        }
        List<ExecutorInfo>executors = executorInfoMapper.selectList(new LambdaQueryWrapper<ExecutorInfo>()
                .in(ExecutorInfo::getInstanceId, instanceIds)
        );

        Map<String, ExecutorInfo> instanceToExecutor = executors.stream()
                .collect(Collectors.toMap(ExecutorInfo::getInstanceId, Function.identity()));
        Set<String> visitedExecutorIds = new HashSet<>();

        for (String instanceId : instanceIds) {
            ExecutorInfo executor = instanceToExecutor.get(instanceId);
            if (executor == null) {
                log.warn("[手动检测] 未找到实例 {} 对应的执行器", instanceId);
                continue;
            }
            if (!visitedExecutorIds.add(executor.getId())) {
                continue;
            }
            collected.addAll(triggerOneExecutor(executor));
        }
        return collected;
    }

    /**
     * 触发所有纳管执行器采集
     */
    private List<MetricReportItemDTO> collectFromAllExecutors() {
        List<MetricReportItemDTO> collected = new ArrayList<>();
        List<ExecutorInfo> executors = executorInfoMapper.selectList(new LambdaQueryWrapper<ExecutorInfo>()
                .eq(ExecutorInfo::getManagementStatus, CommonConstant.MANAGE_ACTIVE)
                .eq(ExecutorInfo::getExecutorStatus,CommonConstant.ONLINE)
        );

        if (executors.isEmpty()) {
            return collected;
        }
        for (ExecutorInfo executor : executors) {
            collected.addAll(triggerOneExecutor(executor));
        }
        return collected;
    }

    /**
     * 调用单个执行器采集，成功时写入 rms_metric_log
     * @param executor
     * @return
     */
    private List<MetricReportItemDTO> triggerOneExecutor(ExecutorInfo executor) {
        List<MetricReportItemDTO> list = new ArrayList<>();
        try {
            ResultBean<List<MetricReportItemDTO>> resp =
                    agentHttpClient.triggerCollect(executor.getHostIp(), executor.getExecutorPort());
            // 平台与执行器统一成功码均为 HTTP 200
            boolean success = resp != null
                    && (resp.getCode() == 200)
                    && resp.getData() != null;
            if (!success) {
                log.warn("[手动检测] 执行器 {} 采集失败: code={}, msg={}",
                        executor.getId(),
                        resp == null ? "null" : resp.getCode(),
                        resp == null ? "null" : resp.getMsg());
                return list;
            }
            for (MetricReportItemDTO item : resp.getData()) {
                list.add(item);
                // 手动采集记录入表
                item.setToken(executor.getExecutorToken());
                item.setCollectType(CommonConstant.MANUAL);
                try {
                    metricLogService.reportMetric(item);
                } catch (Exception e) {
                    log.error("[手动检测] 写入 rms_metric_log 失败, executor={}", executor.getId(), e);
                }
            }
        } catch (Exception e) {
            log.error("[手动检测] 调用执行器异常: {}", executor.getId(), e);
        }
        return list;
    }


    /**
     * 遍历规则 x 采集数据，命中阈值则生成告警记录
     * 分组抑制：同一指标 + 同一实例在同一轮检测中可能同时命中多级阈值
     *
     * @param collected 本轮采集快照（自动检测来自 rms_metric_log 最新值，手动检测来自执行器）
     * @param rules     待检测规则
     * @return 是否新产生/刷新了告警
     */
    private synchronized boolean checkAndGenerateAlarms(List<MetricReportItemDTO> collected, List<AlarmRule> rules) {
        if (collected == null || collected.isEmpty() || rules == null || rules.isEmpty()) {
            return false;
        }
        //按实例索引采集数据
        Map<String, MetricReportItemDTO> itemsByInstance = new HashMap<>();
        for (MetricReportItemDTO item : collected) {
            if (item.getInstanceId() != null) {
                itemsByInstance.put(item.getInstanceId(), item);
            }
        }
        if (itemsByInstance.isEmpty()) {
            return false;
        }

        //构建候选集：规则 × 作用域命中的实例
        List<AlarmCandidate> candidates = new ArrayList<>();
        for (AlarmRule rule : rules) {
            if (rule.getThreshold() == null || rule.getCompareType() == null) {
                continue;
            }
            List<String> scope = resolveScope(rule);   // null = 全部实例；空集 = 配置无效
            if (scope != null && scope.isEmpty()) {
                continue;
            }

            if (scope == null) {
                // 全部实例：与所有采集快照组合
                for (MetricReportItemDTO item : itemsByInstance.values()) {
                    candidates.add(new AlarmCandidate(rule, item));
                }
            } else {
                // 指定实例：只遍历 scope
                for (String instanceId : scope) {
                    MetricReportItemDTO item = itemsByInstance.get(instanceId);
                    if (item != null) {
                        candidates.add(new AlarmCandidate(rule, item));
                    }
                }
            }
        }
        if (candidates.isEmpty()) {
            return false;
        }

        // 历史值按 (metricKey, instanceId, duration) 去重，静默按 (ruleId, instanceId) 去重
        Map<String, List<String>> historyMap = new HashMap<>();
        Map<String, Boolean> silencedMap = new HashMap<>();
        for (AlarmCandidate c : candidates) {
            String hKey = historyKey(c);
            if (!historyMap.containsKey(hKey)) {
                int duration = Math.max(ParseUtils.parseDuration(c.rule.getDuration()), 1);
                historyMap.put(hKey, metricLogMapper.selectRecentMetricValues(
                        c.item.getInstanceId(), c.rule.getMetricKey(), duration));
            }
            String sKey = silenceKey(c);
            if (!silencedMap.containsKey(sKey)) {
                /*判断该规则的该节点是否处于静默期*/
                silencedMap.put(sKey, isAlarmSilenced(c.rule, c.item.getInstanceId()));
            }
        }

        // 命中判定 + 同指标同实例分组，仅保留级别最高（同级别优先节点级规则）的一条
        Map<String, AlarmCandidate> highestByGroup = new LinkedHashMap<>();
        Map<String, Integer> levelByGroup = new HashMap<>();
        Set<String> silencedCandidateKeys = new HashSet<>();

        for (AlarmCandidate c : candidates) {
            String group = groupKey(c);
            /*不满足连续采集周期，跳*/
            if (!isContinuousTriggered(c.rule, historyMap.get(historyKey(c)))) {
                continue;
            }
            String raw = getMetricValue(c.item, c.rule.getMetricKey());
            if (raw == null || raw.trim().isEmpty()) {
                continue;
            }
            double value;
            double threshold;
            try {
                value = Double.parseDouble(raw.trim());
                threshold = Double.parseDouble(c.rule.getThreshold());
            } catch (Exception e) {
                continue;
            }
            // 当前值必须真正越过阈值，避免仅凭历史窗口误判
            if (!CompareUtil.compareValue(value, c.rule.getCompareType(), threshold)) {
                continue;
            }
            // 命中后才记录静默状态；选中最高级别规则后统一判断静默，避免降级成低级别告警
            if (Boolean.TRUE.equals(silencedMap.get(silenceKey(c)))) {
                silencedCandidateKeys.add(silenceKey(c));
            }
            int level = levelOf(c.rule);
            Integer currentLevel = levelByGroup.get(group);
            if (currentLevel == null || level > currentLevel
                    || (level == currentLevel && isMoreSpecific(c, highestByGroup.get(group)))) {
                levelByGroup.put(group, level);
                highestByGroup.put(group, c);
            }
        }

        boolean hasAlarm = false;
        for (Map.Entry<String, AlarmCandidate> entry : highestByGroup.entrySet()) {
            AlarmCandidate c = entry.getValue();
            // 最高级别规则仍处于静默期时，抑制整组，避免降级成低级别告警
            if (silencedCandidateKeys.contains(silenceKey(c))) {
                log.info("[告警检测] 命中但处于静默期，抑制告警: group={}, ruleId={}",
                        entry.getKey(), c.rule.getRuleId());
                continue;
            }
            String raw = getMetricValue(c.item, c.rule.getMetricKey());
            generateAlarmRecord(c.rule, c.item, raw == null ? "" : raw.trim());
            hasAlarm = true;
        }
        return hasAlarm;
    }

    /** 分组键：同一指标 + 同一实例 */
    private static String groupKey(AlarmCandidate c) {
        return c.rule.getMetricKey() + "|" + c.item.getInstanceId();
    }

    /** 同级别时节点级规则优先于全局规则；再比较阈值严格程度 */
    private boolean isMoreSpecific(AlarmCandidate candidate, AlarmCandidate current) {
        if (current == null) {
            return true;
        }
        boolean candidateNode = CommonConstant.SPECIFIED_INSTNACE_ID.equals(candidate.rule.getScopeType());
        boolean currentNode = CommonConstant.SPECIFIED_INSTNACE_ID.equals(current.rule.getScopeType());
        if (candidateNode != currentNode) {
            return candidateNode;
        }
        Double candidateThreshold = ParseUtils.stringToDouble(candidate.rule.getThreshold());
        Double currentThreshold = ParseUtils.stringToDouble(current.rule.getThreshold());
        if (candidateThreshold == null || currentThreshold == null) {
            return false;
        }
        boolean greaterDirection = ">".equals(candidate.rule.getCompareType())
                || ">=".equals(candidate.rule.getCompareType());
        return greaterDirection
                ? candidateThreshold > currentThreshold
                : candidateThreshold < currentThreshold;
    }

    private int levelOf(AlarmRule rule) {
        Integer level = ParseUtils.stringToInteger(rule.getAlarmLevel());
        return level == null ? 1 : level;
    }
    /**
     * 取规则生效节点 scopeInstanceIds
     * @return null = 全部实例；空集 = 指定实例但配置无效；否则为实例集合
     */
    private List<String> resolveScope(AlarmRule rule) {
        if (CommonConstant.ALL_INSTANCE_IDS.equals(rule.getScopeType())) {
            return null;
        }
        String ids = rule.getScopeInstanceId();
        if (ids == null || ids.trim().isEmpty()) {
            return Collections.emptyList();
        }
        List<String> scope = new ArrayList<>();
        for (String id : ids.split(",")) {
            String trimmed = id.trim();
            if (!trimmed.isEmpty()) {
                scope.add(trimmed);
            }
        }
        return scope;
    }

    /**
     * 判断最近 duration 个周期的值是否连续满足比较条件
     * values 为预取的历史值（约定最近优先，mapper 按时间倒序返回），本方法纯内存遍历
     */
    private boolean isContinuousTriggered(AlarmRule rule, List<String> values) {
        int duration = Math.max(ParseUtils.parseDuration(rule.getDuration()), 1);
        if (values == null || values.size() < duration) {
            return false;
        }
        double threshold;
        try {
            threshold = Double.parseDouble(rule.getThreshold());
        } catch (Exception e) {
            return false;
        }
        for (int i = 0; i < duration; i++) {
            String v = values.get(i);
            if (v == null || v.trim().isEmpty()) {
                return false;
            }
            try {
                if (!CompareUtil.compareValue(Double.parseDouble(v.trim()), rule.getCompareType(), threshold)) {
                    return false;
                }
            } catch (Exception e) {
                return false;
            }
        }
        return true;
    }

    private static String historyKey(AlarmCandidate c) {
        int duration = Math.max(ParseUtils.parseDuration(c.rule.getDuration()), 1);
        return c.rule.getMetricKey() + "|" + c.item.getInstanceId() + "|" + duration;
    }

    private static String silenceKey(AlarmCandidate c) {
        return c.rule.getId() + "|" + c.item.getInstanceId();
    }

    /** 候选：规则 + 命中的采集快照 */
    private static final class AlarmCandidate {
        private final AlarmRule rule;
        private final MetricReportItemDTO item;

        AlarmCandidate(AlarmRule rule, MetricReportItemDTO item) {
            this.rule = rule;
            this.item = item;
        }
    }

    /**
     * 静默期重复告警抑制 silenceDuration 单位秒
     * @param rule
     * @param instanceId
     * @return
     */
    private boolean isAlarmSilenced(AlarmRule rule, String instanceId) {
        String silenceDuration = rule.getSilenceDuration();
        if (silenceDuration == null || silenceDuration.trim().isEmpty()) {
            return false;
        }
        long seconds;
        try {
            seconds = Long.parseLong(silenceDuration.trim());
        } catch (Exception e) {
            return false;
        }
        if (seconds <= 0) {
            return false;
        }
        LocalDateTime since = LocalDateTime.now().minusSeconds(seconds);

        long count = alarmRecordMapper.selectCount(
                new LambdaQueryWrapper<AlarmRecord>()
                        .eq(AlarmRecord::getRuleId, rule.getRuleId())
                        .eq(AlarmRecord::getInstanceId, instanceId)
                        .ge(AlarmRecord::getTriggerTime, since));



        return count > 0;
    }


    /**
     * 从上报项中取指标值
     */
    private String getMetricValue(MetricReportItemDTO item, String metricKey) {
        if (metricKey == null) {
            return null;
        }
        switch (metricKey) {
            case "cpuUsage":
                return item.getCpuPer();
            case "serverMemUsage":
                return item.getMemServerPer();
            case "redisMemUsed":
                return item.getMemUs();
            case "qps":
                return item.getOps();
            case "avgLatency":
                return item.getLatency();
            case "netInputBandwidth":
                return item.getIoIik();
            case "netOutputBandwidth":
                return item.getIoIok();
            case REPL_DELAY_METRIC_KEY:
                return item.getReplDelay();
            case "pingLatency":
                return item.getPingLatency();
            default:
                return null;
        }
    }

    /**
     * 生成（或刷新）一条告警记录
     *
     * 同一规则 + 同一实例已有未恢复告警时，不重复插入新记录，
     * 只刷新触发值/告警内容/更新时间，实现“抑制/分组治理”，
     * 避免每轮检测都产生一条重复告警
     *
     * @param rule         命中规则
     * @param item         采集快照
     * @param triggerValue 当前指标值（保留原始精度）
     */
    private void generateAlarmRecord(AlarmRule rule, MetricReportItemDTO item, String triggerValue) {
        try {
            String content = buildAlarmContent(rule, triggerValue);
            LocalDateTime now = LocalDateTime.now();

            AlarmRecord existing = alarmRecordMapper.selectOne(Wrappers.<AlarmRecord>lambdaQuery()
                    .eq(AlarmRecord::getRuleId, rule.getRuleId())
                    .eq(AlarmRecord::getInstanceId, item.getInstanceId())
                    .eq(AlarmRecord::getRecoverStatus, CommonConstant.UNRECOVER)
                    .orderByDesc(AlarmRecord::getTriggerTime)
                    .last("LIMIT 1"));

            if (existing != null) {
                alarmRecordMapper.update(null, new LambdaUpdateWrapper<AlarmRecord>()
                        .set(AlarmRecord::getTriggerValue, triggerValue)
                        .set(AlarmRecord::getAlarmContent, content)
                        .set(AlarmRecord::getThreshold, rule.getThreshold())
                        .set(AlarmRecord::getUnit, rule.getUnit())
                        .set(AlarmRecord::getAlarmLevel, rule.getAlarmLevel())
                        .set(AlarmRecord::getUpdatedTime, now)
                        .eq(AlarmRecord::getId, existing.getId()));
                log.info("[告警检测] 已存在未恢复告警，刷新触发值: alarmNo={}, value={}",
                        existing.getAlarmNo(), triggerValue);
                return;
            }

            AlarmRecord record = new AlarmRecord();
            record.setAlarmNo("AL_" + IdUtil.getSnowflakeNextIdStr());
            record.setRuleId(rule.getRuleId());
            record.setInstanceId(item.getInstanceId());
            record.setMetricKey(rule.getMetricKey());
            record.setMetricName(metricNameOf(rule));
            record.setAlarmLevel(rule.getAlarmLevel());
            record.setAlarmContent(content);
            record.setTriggerValue(triggerValue);
            record.setThreshold(rule.getThreshold());
            record.setUnit(rule.getUnit());
            record.setTriggerTime(now);
            record.setRecoverStatus(StatusEnum.UNRECOVER.getCode());
            record.setCreatedTime(now);
            record.setUpdatedTime(now);
            alarmRecordMapper.insert(record);
            /* 生成告警记录后邮件通知，并记录通知发送结果 */
            alarmNoticeService.sendAlarmNotice(record, rule);
        } catch (Exception e) {
            log.error("[告警检测] 生成告警记录失败, ruleId={}", rule.getRuleId(), e);
        }
    }

    /** 按比较方式生成可读告警内容 */
    private String buildAlarmContent(AlarmRule rule, String triggerValue) {
        String metricName = metricNameOf(rule);
        String relation;
        switch (rule.getCompareType() == null ? "" : rule.getCompareType()) {
            case ">":
                relation = "超过";
                break;
            case ">=":
                relation = "达到或超过";
                break;
            case "<":
                relation = "低于";
                break;
            case "<=":
                relation = "达到或低于";
                break;
            case "=":
                relation = "等于";
                break;
            default:
                relation = "触发阈值";
        }
        return String.format("指标【%s】当前值 %s%s %s阈值 %s%s",
                metricName, triggerValue, nullToEmpty(rule.getUnit()),
                relation, rule.getThreshold(), nullToEmpty(rule.getUnit()));
    }

    /** 兼容历史数据里 metricName 为空的规则 */
    private String metricNameOf(AlarmRule rule) {
        if (rule.getMetricName() != null && !rule.getMetricName().trim().isEmpty()) {
            return rule.getMetricName();
        }
        MetricTypeEnum metricType = MetricTypeEnum.of(rule.getMetricKey());
        return metricType != null ? metricType.getName() : rule.getMetricKey();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    // ========================================================================
    // 告警自动恢复：指标连续 N 个采集周期回落阈值以下自动恢复
    // ========================================================================

    /**
     * 自动恢复检查：
     * 1) 开关关闭（periods<=0）直接返回，rules非null 非空
     * 2) 收集本次检测涉及的 ruleId，一次性查询这些规则下的未恢复告警（自定义 Mapper）；
     * 3) 逐条判断最近 N 条采集值是否全部不满足告警条件，满足则自动恢复
     *
     * @param rules 本次检测涉及的规则列表（单规则=单条，全量=全部启用规则）
     */
    private void checkAndAutoRecover(List<AlarmRule> rules) {
        //rules非空非null非禁用
        //自动恢复开关：periods<=0 视为关闭
        if (autoRecoverPeriods <= 0) {
            return;
        }


        //构建 ruleId
        Map<String, AlarmRule> ruleMap = new HashMap<>();
        List<String> ruleIds = new ArrayList<>(rules.size());
        for (AlarmRule rule : rules) {
            if (rule.getRuleId() == null) {
                continue;
            }
            if (ruleMap.put(rule.getRuleId(), rule) == null) {
                ruleIds.add(rule.getRuleId());
            }
        }
        if (ruleIds.isEmpty()) {
            return;
        }

        // 查这些规则下的未恢复告警
        List<AlarmRecord> unrecovered = alarmRecordMapper.selectList(new LambdaQueryWrapper<AlarmRecord>()
                .eq(AlarmRecord::getRecoverStatus,CommonConstant.UNRECOVER)
                .in(AlarmRecord::getRuleId, ruleIds)
                .orderByAsc(AlarmRecord::getTriggerTime)
        );

        if (unrecovered.isEmpty()) {
            return;
        }

        //逐条判断是否满足自动恢复条件
        for (AlarmRecord record : unrecovered) {
            if (record.getRuleId() == null) {
                continue;
            }
            AlarmRule rule = ruleMap.get(record.getRuleId());
            if (rule == null) {
                // 规则不在本次检测范围（已禁用/已删除），跳过
                continue;
            }
            try {
                if (isRecoveredByRecentValues(rule, record)) {
                    autoRecover(record);
                }
            } catch (Exception e) {
                log.error("[自动恢复] 检查失败, alarmRecordId={}", record.getId(), e);
            }
        }
    }

    /**
     * 判断告警是否满足自动恢复条件：
     * 取该实例该指标最近 autoRecoverPeriods 条采集值，全部不满足告警条件则已恢复
     * 以下情况保守返回 false：
     * - 规则阈值/比较方式缺失或解析失败；
     * - 采集值不足 N 条；
     * - 任意一条为 null / 空串 / 解析失败；
     * - 任意一条仍满足告警条件
     */
    private boolean isRecoveredByRecentValues(AlarmRule rule, AlarmRecord record) {
        if (rule.getThreshold() == null || rule.getCompareType() == null) {
            return false;
        }
        double threshold;
        try {
            threshold = Double.parseDouble(rule.getThreshold());
        } catch (Exception e) {
            return false;
        }
        //取最近 N 条采集值
        List<String> values = metricLogMapper.selectRecentMetricValues(record.getInstanceId(), rule.getMetricKey(), autoRecoverPeriods);
        if (values == null || values.size() < autoRecoverPeriods) {
            return false;
        }
        //任意一条仍满足告警条件则未恢复
        for (String v : values) {
            if (v == null || v.trim().isEmpty()) {
                return false;
            }
            try {
                double val = Double.parseDouble(v.trim());
                if (CompareUtil.compareValue(val, rule.getCompareType(), threshold)) {
                    return false;
                }
            } catch (Exception e) {
                return false;
            }
        }
        return true;
    }

    /**
     * 自动恢复
     * 更新 recover_status=1、recover_time=当前时间、updated_time=当前时间；
     * 记入操作审计日志（operationType=自动恢复，与人工恢复区分）
     */
    private void autoRecover(AlarmRecord record) {
        LocalDateTime now = LocalDateTime.now();
        int rows = alarmRecordMapper.update(null,
                new LambdaUpdateWrapper<AlarmRecord>()
                        .set(AlarmRecord::getRecoverStatus, CommonConstant.RECOVER)
                        .set(AlarmRecord::getRecoverTime,now)
                        .set(AlarmRecord::getUpdatedTime,now)
                        .eq(AlarmRecord::getId, record.getId())
        );

        if (rows > 0) {
            //自动恢复记入操作审计日志
            auditLogService.record(CommonConstant.MODULE_ALARM_RECORD, CommonConstant.OPERATION_TYPE_ALARM_AUTO_RECOVERY, record.getId(),
                    "指标连续" + autoRecoverPeriods + "周期回落阈值以下，自动恢复告警："
                            + record.getAlarmNo());
            log.info("[自动恢复] 告警已自动恢复, alarmNo={}, periods={}",
                    record.getAlarmNo(), autoRecoverPeriods);
        }
    }


}



