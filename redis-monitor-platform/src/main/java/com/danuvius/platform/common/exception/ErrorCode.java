package com.danuvius.platform.common.exception;

public enum ErrorCode implements ErrorCodeInfo {
    OK(200,"OK"),

    /** 请求参数错误 */
    PARAM_ERROR(40000, "请求参数错误"),

    /** 未登录 */
    NOT_LOGIN(40100, "未登录"),

    /** 无权限 */
    NO_AUTH(40101, "无权限"),

    /** 禁止访问 */
    FORBIDDEN(40300, "禁止访问"),

    /** 资源不存在 */
    NOT_FOUND(40400, "请求参数不存在"),

    /** 系统内部异常 */
    SYSTEM_ERROR(50000, "系统内部异常"),

    /** 操作失败 */
    OPERATION_ERROR(50001, "操作失败");

    private final int code;
    private final String msg;
    ErrorCode(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }
    public int getCode() {
        return code;
    }
    public String getMsg() {
        return msg;
    }
    public String toString() {return String.format("ErrorCode{code=%d,msg='%s'}", code, msg);}
}
