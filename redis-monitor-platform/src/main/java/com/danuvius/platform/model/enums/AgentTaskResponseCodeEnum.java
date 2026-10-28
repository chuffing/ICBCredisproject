package com.danuvius.platform.model.enums;

/**
 * 平台与执行器之间的任务接收业务响应码。
 *
 * <p>该枚举只表示统一响应体中的业务结果，不表示真实HTTP状态码。</p>
 */
public enum AgentTaskResponseCodeEnum {

    ACCEPTED(202, "任务已接收"),

    BUSINESS_REJECTED(30000, "执行器业务处理失败"),

    PARAMETER_REJECTED(400, "任务参数错误"),

    AUTHENTICATION_REJECTED(401, "身份认证失败"),

    PAYLOAD_TOO_LARGE(413, "上传内容过大"),

    AGENT_BUSY(503, "执行器繁忙");

    private final int code;

    private final String defaultMsg;

    AgentTaskResponseCodeEnum(int code, String defaultMsg) {
        this.code = code;
        this.defaultMsg = defaultMsg;
    }

    public int getCode() {
        return code;
    }

    public String getDefaultMsg() {
        return defaultMsg;
    }

    /**
     * 根据业务响应码获取对应枚举。
     *
     * @param code 业务响应码
     * @return 对应枚举；未定义的业务响应码返回null
     */
    public static AgentTaskResponseCodeEnum fromCode(int code) {
        // 兼容旧执行器使用的40000段参数错误码。
        if (code >= 40000 && code < 40100) {
            return PARAMETER_REJECTED;
        }

        // 新执行器按照统一规范返回50000段参数错误码。
        if (code >= 50000 && code < 60000) {
            return PARAMETER_REJECTED;
        }

        // 执行器已明确返回业务异常，任务应当判定为拒绝而不是结果未知。
        if (code >= 30000 && code < 40000) {
            return BUSINESS_REJECTED;
        }

        for (AgentTaskResponseCodeEnum responseCode : values()) {
            if (responseCode.code == code) {
                return responseCode;
            }
        }
        return null;
    }
}
