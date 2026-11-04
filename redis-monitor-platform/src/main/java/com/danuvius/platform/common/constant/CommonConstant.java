package com.danuvius.platform.common.constant;

import com.danuvius.platform.model.enums.metric.MetricTypeEnum;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 通用常量
 */
public final class CommonConstant {

    public static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    public static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    public static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");


    /**  告警级别合法值 */
    public static final List<String> ALARM_LEVELS = list("1", "2", "3");
    /**  比较方式合法值 */
    public static final List<String> COMPARE_TYPES = list(">", "<", ">=", "<=", "=");
    /**  生效范围合法值 */
    public static final List<String> SCOPE_TYPES = list("1", "2");
    /**  启用状态合法值 */
    public static final List<String> ENABLE_STATUS = list("0", "1");
    /**  恢复状态合法值 */
    public static final List<String> RECOVER_STATUS = list("0", "1");
    /*执行器状态合法值*/
    public static final List<String> EXECUTOR_STATUS = list("0", "1");

    /**  纳管状态合法值 */
    public static final List<String> MANAGEMENT_STATUS = list("0", "1");
    /**  趋势时间范围合法值 */
    public static final List<String> TIME_RANGES = list("近1h", "近6h");
    /**  报告类型合法值 */
    public static final List<String> REPORT_TYPES = list("日报", "周报");

    /**
     * 告警规则的指标 key
     */
    public static final List<String> RULE_METRIC_KEYS = Collections.unmodifiableList(
            MetricTypeEnum.RULE_METRICS.stream()
                    .map(MetricTypeEnum::getKey)
                    .collect(Collectors.toList()));

    /** 同一指标 + 同一生效范围下的规则级别数上限（提示/警告/严重各一条） */
    public static final int MAX_RULE_LEVELS_PER_SCOPE = 3;
    /** 同一指标允许配置的启用规则总数上限，避免为同一指标叠加过多细节规则 */
    public static final int MAX_RULES_PER_METRIC = 30;

    public static final List<MetricTypeEnum> CORE_METRICS = Collections.unmodifiableList(Arrays.asList(
            MetricTypeEnum.CPU_USAGE,
            MetricTypeEnum.SERVER_MEM_USAGE,
            MetricTypeEnum.REDIS_MEM_USED,
            MetricTypeEnum.QPS,
            MetricTypeEnum.AVG_LATENCY,
            MetricTypeEnum.NET_INPUT_BANDWIDTH,
            MetricTypeEnum.NET_OUTPUT_BANDWIDTH
    ));

    /**
     * 单位合法值
     */
    public static final List<String> UNIT_TYPES = Collections.unmodifiableList(
            Arrays.stream(MetricTypeEnum.values())
                    .map(MetricTypeEnum::getUnit)
                    .distinct()
                    .collect(Collectors.toList()));

    public static final List<String> ROLE_TYPES = list("MASTER", "REPLICA");
    /** Redis run_id：40 位小写十六进制 */
    public static final Pattern RUN_ID_PATTERN = Pattern.compile("^[0-9a-f]{40}$");
    /** 版本号：6.2 / 6.2.7 / 7.0.0-rc1 */
    public static final Pattern VERSION_PATTERN = Pattern.compile("^\\d+\\.\\d+(\\.\\d+)?([-.].*)?$");
    /** host：域名 / IPv4 / IPv6（含 [] 与 :） */
    public static final Pattern HOST_PATTERN = Pattern.compile("^[A-Za-z0-9.:\\-_\\[\\]]+$");

    /*AuditLog表 module字段 key*/
    public static final List<String> AUDIT_MODULE_KEYS = list("rule", "report", "operations");
    /*AuditLog表 operation_type字段 key*/
    public static final List<String> AUDIT_OPERATION_TYPES = list(
            "EXPORT",
            "ADD", "UPDATE", "DELETE",
            "FAULT_RECOVERY", "VERSION_UPGRADE", "SERVER_SWITCH");

    public static final List<String> WHITE_URL_LIST = list(
            "/static/**"
    );

    public static final String MODULE_ALARM_RULE = "规则模块";
    public static final String MODULE_ALARM_RECORD = "记录模块";
    public static final String MODULE_REPORT = "报告模块";
    public static final String MODULE_OPERATIONS = "运维模块";

    public static final String OPERATION_TYPE_EXPORT = "导出";
    public static final String OPERATION_TYPE_ADD = "新增";
    public static final String OPERATION_TYPE_UPDATE = "修改";
    public static final String OPERATION_TYPE_DELETE = "删除";
    public static final String OPERATION_TYPE_ALARM_AUTO_RECOVERY = "告警自动恢复";
    public static final String OPERATION_TYPE_ALARM_MANUAL_RECOVERY = "告警人工恢复";
    public static final String OPERATION_TYPE_FAULT_RECOVERY = "故障恢复";
    public static final String OPERATION_TYPE_VERSION_UPGRADE = "版本更新";
    public static final String OPERATION_TYPE_SERVER_SWITCH = "服务器切换";

    /*单位常量*/
    public static final String UNIT_PERCENT = "%";
    public static final String UNIT_GB = "GB";
    public static final String UNIT_TIMES_PER_SECOND = "次/秒";
    public static final String UNIT_MILLISECOND = "ms";
    public static final String UNIT_SECOND = "s";
    public static final String UNIT_KB_PER_SECOND = "KB/s";
    public static final String UNIT_MICROSECOND = "μs";

    /*报告redis节点可用率/节点数据完整度：实际采集点数 / 理论采集点数（5分钟一个采集周期）判断*/
    public static final BigDecimal RATE_EXCELLENT = new BigDecimal("95.00");
    public static final BigDecimal RATE_STABLE = new BigDecimal("85.00");
    public static final BigDecimal RATE_WARNING = new BigDecimal("75.00");

    /**
     * executor_info表 / sentinel_node_info表
     */
    /*executorStatus / sentinelStatus*/
    public static final String ONLINE_DESC = "在线";
    public static final String OFFLINE_DESC = "离线";
    public static final String OFFLINE = "0";
    public static final String ONLINE = "1";

    public static final String TOPOLOGY_HEALTH = "健康";
    public static final String TOPOLOGY_WARNING = "警告";
    public static final String TOPOLOGY_ANOMALY = "异常";

    public static final String REPORT_TYPE_DAY = "日报";
    public static final String REPORT_TYPE_WEEK = "周报";

    public static final String NEAR_1HOUR = "近1h";

    /*日周报报告，告警与主从切换统计前端表格类型项*/
    public static final String MASTER_SWITCH = "masterSwitch";
    public static final String MASTER_SWITCH_DESC = "主从切换";
    public static final String ALARM = "告警";

    public static final String MASTER = "MASTER";
    public static final String REPLICA = "REPLICA";

    public static final String SUCCESS = "成功";
    public static final String FAILURE = "失败";


    /*recoverStatus*/
    public static final String UNRECOVER = "0";
    public static final String RECOVER = "1";
    public static final String UNRECOVER_DEC = "未恢复";
    public static final String RECOVER_DEC = "已恢复";


    /**
     * redis_switch_event表
     */
    /*switchResult*/
    public static final String SWITCH_FAILURE = "0";
    public static final String SWITCH_SUCCESS = "1";

    /*ReplDelayStatus*/
    public static final String NORMAL = "正常";
    public static final String DELAY = "延迟";

    /*最近记录入参*/
    public static final Integer DEFAULT_RECENT = 5;
    /*redis默认端口*/
    public static final String DEFAULT_REDIS_PORT = "6379";
    public static final String DEFAULT_ROLE_MASTER = "MASTER";
    public static final String DEFAULT_ROLE_REPLICA = "REPLICA";
    public static final String DEFAULT_COLLECT_INTERVAL = "300";
    public static final String DEFAULT_DURATION = "60";
    public static final String DEFAULT_ALARM_LEVEL = "2";
    public static final String DEFAULT_SILENCE_DURATION = "1800";
    public static final String DEFAULT_SCOPE_TYPE = "1";

    private CommonConstant() {
    }

    /** 构造不可变字符串列表 */
    private static List<String> list(String... values) {
        return Collections.unmodifiableList(Arrays.asList(values));
    }
}
