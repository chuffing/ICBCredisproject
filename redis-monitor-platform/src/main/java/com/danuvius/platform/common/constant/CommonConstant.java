package com.danuvius.platform.common.constant;

import com.danuvius.platform.model.enums.metric.MetricTypeEnum;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

public class CommonConstant {
    /** [校验] 告警级别合法值 */
    public static final List<String> ALARM_LEVELS = Arrays.asList("1", "2", "3");
    /** [校验] 比较方式合法值 */
    public static final List<String> COMPARE_TYPES = Arrays.asList(">", "<", ">=", "<=", "=");
    /** [校验] 生效范围合法值 */
    public static final List<String> SCOPE_TYPES = Arrays.asList("1", "2");
    /** [校验] 启用状态合法值 */
    public static final List<String> ENABLE_STATUS = Arrays.asList("0", "1");
    /** [校验] 恢复状态合法值 */
    public static final List<String> RECOVER_STATUS = Arrays.asList("0", "1");
    /*执行器状态合法值*/
    public static final List<String> EXECUTOR_STATUS = Arrays.asList("0", "1");

    /** [校验] 纳管状态合法值 */
    public static final List<String> MANAGEMENT_STATUS = Arrays.asList("0", "1");
    /** [校验] 趋势时间范围合法值 */
    public static final List<String> TIME_RANGES = Arrays.asList("近1h", "近6h");
    /** [校验] 报告类型合法值 */
    public static final List<String> REPORT_TYPES = Arrays.asList("日报", "周报");
    /** [校验] 趋势指标 key 合法值（与 MetricTypeEnum 采集指标对齐） */
    public static final List<String> METRIC_KEYS = Arrays.asList(
            "cpuUsage", "serverMemUsage", "redisMemUsed", "qps", "avgLatency",
            "netInputBandwidth", "netOutputBandwidth",
            "nodeRunStatus","sentinelStatus","replDelay","pingLatency"
    );

    public static final List<MetricTypeEnum> CORE_METRICS = Arrays.asList(
            MetricTypeEnum.CPU_USAGE,
            MetricTypeEnum.SERVER_MEM_USAGE,
            MetricTypeEnum.REDIS_MEM_USED,
            MetricTypeEnum.QPS,
            MetricTypeEnum.AVG_LATENCY,
            MetricTypeEnum.NET_INPUT_BANDWIDTH,
            MetricTypeEnum.NET_OUTPUT_BANDWIDTH
    );


    /** [校验] 趋势指标 name 合法值（与 MetricTypeEnum 采集指标对齐） */
    public static final List<String> METRIC_NAMES = Arrays.asList(
            "CPU使用率", "服务器内存使用率", "Redis内存使用量", "QPS", "平均延迟",
            "网络输入带宽", "网络输出带宽",
            "节点存活状态","哨兵状态","主从复制延迟","连通性延迟"
    );

    /** [校验] 单位取值值 */
    public static final List<String> UNIT_TYPES = Arrays.asList("%", "GB","次/秒","ms","s","KB/s","μs");

    public static final List<String> ROLE_TYPES = Arrays.asList("MASTER", "REPLICA");
    /** Redis run_id：40 位小写十六进制 */
    public static final Pattern RUN_ID_PATTERN = Pattern.compile("^[0-9a-f]{40}$");
    /** 版本号：6.2 / 6.2.7 / 7.0.0-rc1 */
    public static final Pattern VERSION_PATTERN = Pattern.compile("^\\d+\\.\\d+(\\.\\d+)?([-.].*)?$");
    /** host：域名 / IPv4 / IPv6（含 [] 与 :） */
    public static final Pattern HOST_PATTERN = Pattern.compile("^[A-Za-z0-9.:\\-_\\[\\]]+$");

    /*AuditLog表 module字段 key*/
    public static final List<String> AUDIT_MODULE_KEYS = Arrays.asList("rule","report","operations");
    /*AuditLog表 operation_type字段 key*/
    public static final List<String> AUDIT_OPERATION_TYPES = Arrays.asList("EXPORT",
            "ADD","UPDATE","DELETE",
            "FAULT_RECOVERY","VERSION_UPGRADE","SERVER_SWITCH");

    public final static List<String> WHITE_URL_LIST = Arrays.asList(
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
    public static final String OPERATION_TYPE_ALARM_RECOVERY = "告警恢复";
    public static final String OPERATION_TYPE_FAULT_RECOVERY = "故障恢复";
    public static final String OPERATION_TYPE_VERSION_UPGRADE = "版本更新";
    public static final String OPERATION_TYPE_SERVER_SWITCH = "服务器切换";


    public static final String TOPOLOGY_HEALTH = "健康";
    public static final String TOPOLOGY_WARNING = "警告";
    public static final String TOPOLOGY_ANOMALY = "异常";


    public static final String REPORT_TYPE_DAY = "日报";
    public static final String REPORT_TYPE_WEEK = "周报";


    public static final String NEAR_1HOUR = "近1h";

    /*日周报报告，告警与主从切换统计前端表格类型项*/
    public static final String MASTER_SWITCH = "masterSwitch";
    public static final String MASTER_SWITCH_DESC = "主从切换";






    public static final String SUCCESS = "成功";
    public static final String FAILURE = "失败";

    public static final String ENABLE_DEC = "启用";
    public static final String DISABLE_DEC = "禁用";
    public static final String DISABLE = "0";
    public static final String ENABLE = "1";

    public static final String OFFLINE = "0";
    public static final String ONLINE = "1";

    public static final String RUN_OFFLINE = "0";
    public static final String RUN_NORMAL = "1";
    public static final String RUN_WARNING = "2";
    public static final String RUN_CRITICAL = "3";



    public static final String ALL_INSTANCE_IDS = "1";
    public static final String SPECIFIED_INSTNACE_ID = "2";




    public static final String MANAGE_INACTIVE = "0";
    public static final String MANAGE_INACTIVE_DESC = "未纳管";
    public static final String MANAGE_ACTIVE = "1";
    public static final String MANAGE_ACTIVE_DESC = "已纳管";


    public static final String AUTO = "0";
    public static final String MANUAL = "1";

    public static final String NOT_DELETED = "0";
    public static final String DELETED = "1";


    /*recoverStatus*/
    public static final String UNRECOVER = "0";
    public static final String RECOVER = "1";
    public static final String UNRECOVER_DEC = "未恢复";
    public static final String RECOVER_DEC = "已恢复";

    /**
     * redis_switch_event表
     */
    /*switchType*/
    /*
    *       INIT("0", "基线"),
    *       AUTO("1", "自动切换"),
    *       MANUAL("2", "手动切换"),
    *       OTHER("3", "其他"),
    * */
    public static final String SWITCH_INIT ="0";
    public static final String SWITCH_AUTO="1";
    public static final String SWITCH_MANUAL="2";
    public static final String SWITCH_OTHER="3";

    /**
     * redis_switch_event表
     */
    /*switchResult*/
    public static final String SWITCH_FAILURE ="0";
    public static final String SWITCH_SUCCESS ="1";


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
    public static final String DEFAULT_DURATION         = "60";
    public static final String DEFAULT_ALARM_LEVEL      = "2";
    public static final String DEFAULT_SILENCE_DURATION = "1800";
    public static final String DEFAULT_SCOPE_TYPE       = "1";




}
