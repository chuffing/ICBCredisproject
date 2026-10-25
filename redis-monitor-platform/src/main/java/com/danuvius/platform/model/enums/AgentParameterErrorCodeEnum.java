package com.danuvius.platform.model.enums;

/**
 * 执行器参数校验错误码。
 *
 * <p>该枚举属于平台与执行器之间的通信协议，修改时需要同步修改执行器中的
 * {@code ParameterErrorCode}。</p>
 */
public enum AgentParameterErrorCodeEnum {

    /** 任务编号不合法。 */
    TASK_NO_INVALID("PARAM_001", "任务编号不合法"),

    /** 步骤编号不合法。 */
    STEP_NO_INVALID("PARAM_002", "步骤编号不合法"),

    /** 任务类型不能为空。 */
    TASK_TYPE_EMPTY("PARAM_003", "任务类型不能为空"),

    /** 任务类型不支持。 */
    TASK_TYPE_UNSUPPORTED("PARAM_004", "任务类型不支持"),

    /** 任务参数不合法。 */
    TASK_PARAM_INVALID("PARAM_005", "任务参数不合法"),

    /** 安装包不合法。 */
    PACKAGE_FILE_INVALID("PARAM_006", "安装包不合法"),

    /** 安装包摘要不一致。 */
    PACKAGE_CHECKSUM_MISMATCH("PARAM_007", "安装包摘要不一致"),

    /** 配置文件不合法。 */
    CONFIG_FILE_INVALID("PARAM_008", "配置文件不合法"),

    /** 配置文件摘要不一致。 */
    CONFIG_CHECKSUM_MISMATCH("PARAM_009", "配置文件摘要不一致");

    /** 错误码。 */
    private final String code;

    /** 错误说明。 */
    private final String msg;

    /**
     * 创建执行器参数错误码。
     *
     * @param code 错误码
     * @param msg 错误说明
     */
    AgentParameterErrorCodeEnum(String code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    public String getCode() {
        return code;
    }

    public String getMsg() {
        return msg;
    }
}
