package com.danuvius.platform.common.exception;

/**
 * 参数校验异常码，统一使用50000段。
 */
public enum ParameterCode {

    /** 通用参数错误 */
    PARAM_ERROR(50000, "请求参数错误"),

    /** 参数为空（null） */
    PARAM_NULL(50001, "参数不能为空"),

    /** 参数为空白字符串 */
    PARAM_BLANK(50002, "参数不能为空白字符串"),

    /** 参数格式不正确 */
    PARAM_FORMAT_ERROR(50003, "参数格式不正确"),

    /** 参数长度不符合要求 */
    PARAM_LENGTH_ERROR(50004, "参数长度不符合要求"),

    /** 参数数值超出允许范围 */
    PARAM_RANGE_ERROR(50005, "参数数值超出允许范围"),

    /** 参数类型不正确（转换失败） */
    PARAM_TYPE_ERROR(50006, "参数类型不正确"),

    /** 参数不在允许的枚举范围内 */
    PARAM_ENUM_ERROR(50007, "参数不在允许的枚举范围内"),

    /** 参数不匹配指定正则 */
    PARAM_REGEX_ERROR(50008, "参数格式不正确"),

    /** 集合参数为空 */
    PARAM_COLLECTION_EMPTY(50009, "集合参数不能为空"),

    /** 集合大小不符合要求 */
    PARAM_COLLECTION_SIZE_ERROR(50010, "集合大小不符合要求"),

    /** 上传文件为空 */
    PARAM_FILE_EMPTY(50011, "上传文件不能为空"),

    /** 文件扩展名不允许 */
    PARAM_FILE_EXTENSION_ERROR(50012, "文件格式不正确，请重试"),

    /** 文件大小超出限制 */
    PARAM_FILE_SIZE_ERROR(50013, "文件大小超出限制"),

    /** 文件名为空或格式不正确 */
    PARAM_FILE_NAME_ERROR(50014, "文件名为空或格式不正确"),

    /** 日期格式不正确 */
    PARAM_DATE_FORMAT_ERROR(50015, "日期格式不正确"),

    /** 日期超出范围 */
    PARAM_DATE_RANGE_ERROR(50016, "日期超出允许范围"),

    /** ID 格式不正确 */
    PARAM_ID_ERROR(50017, "ID 格式不正确"),

    /** 参数对象不存在 */
    PARAM_NOT_EXIST(50018, "参数对象不存在"),

    /** 参数重复 */
    PARAM_DUPLICATE(50019, "参数重复"),

    /** 参数非法 */
    PARAM_INVALID(50020, "参数非法"),

    /** Redis安装包不合法 */
    PACKAGE_FILE_INVALID(50021, "安装包不合法");

    private Integer code;
    private String msg;

    ParameterCode(Integer code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    public Integer getCode() {
        return code;
    }

    public void setCode(Integer code) {
        this.code = code;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }
}
