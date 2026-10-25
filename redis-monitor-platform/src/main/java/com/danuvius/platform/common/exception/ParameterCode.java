package com.danuvius.platform.common.exception;

/**
 * 参数校验错误码
 */
public enum ParameterCode {

    /** 通用参数错误 */
    PARAM_ERROR(40000, "请求参数错误"),

    /** 参数为空（null） */
    PARAM_NULL(40001, "参数不能为空"),

    /** 参数为空白字符串 */
    PARAM_BLANK(40002, "参数不能为空白字符串"),

    /** 参数格式不正确 */
    PARAM_FORMAT_ERROR(40003, "参数格式不正确"),

    /** 参数长度不符合要求 */
    PARAM_LENGTH_ERROR(40004, "参数长度不符合要求"),

    /** 参数数值超出允许范围 */
    PARAM_RANGE_ERROR(40005, "参数数值超出允许范围"),

    /** 参数类型不正确（转换失败） */
    PARAM_TYPE_ERROR(40006, "参数类型不正确"),

    /** 参数不在允许的枚举范围内 */
    PARAM_ENUM_ERROR(40007, "参数不在允许的枚举范围内"),

    /** 参数不匹配指定正则 */
    PARAM_REGEX_ERROR(40008, "参数格式不正确"),

    /** 集合参数为空 */
    PARAM_COLLECTION_EMPTY(40009, "集合参数不能为空"),

    /** 集合大小不符合要求 */
    PARAM_COLLECTION_SIZE_ERROR(40010, "集合大小不符合要求"),

    /** 上传文件为空 */
    PARAM_FILE_EMPTY(40011, "上传文件不能为空"),

    /** 文件扩展名不允许 */
    PARAM_FILE_EXTENSION_ERROR(40012, "文件格式不正确，请重试"),

    /** 文件大小超出限制 */
    PARAM_FILE_SIZE_ERROR(40013, "文件大小超出限制"),

    /** 文件名为空或格式不正确 */
    PARAM_FILE_NAME_ERROR(40014, "文件名为空或格式不正确"),

    /** 日期格式不正确 */
    PARAM_DATE_FORMAT_ERROR(40015, "日期格式不正确"),

    /** 日期超出范围 */
    PARAM_DATE_RANGE_ERROR(40016, "日期超出允许范围"),

    /** ID 格式不正确 */
    PARAM_ID_ERROR(40017, "ID 格式不正确"),

    /** 参数对象不存在 */
    PARAM_NOT_EXIST(40018, "参数对象不存在"),

    /** 参数重复 */
    PARAM_DUPLICATE(40019, "参数重复"),

    /** 参数非法 */
    PARAM_INVALID(40020, "参数非法"),

    /** Redis安装包不合法 */
    PACKAGE_FILE_INVALID(40021, "安装包不合法"),

    /** Redis安装包摘要不一致 */
    PACKAGE_CHECKSUM_MISMATCH(40022, "安装包摘要不一致"),

    /** Redis配置文件不合法 */
    CONFIG_FILE_INVALID(40023, "配置文件不合法"),

    /** Redis配置文件摘要不一致 */
    CONFIG_CHECKSUM_MISMATCH(40024, "配置文件摘要不一致");

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
