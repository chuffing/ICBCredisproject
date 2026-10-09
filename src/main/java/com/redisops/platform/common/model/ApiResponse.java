package com.redisops.platform.common.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 平台提供给执行器的统一响应结构。
 *
 * @param <T> data 字段类型
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private final String code;
    private final String message;
    private final T data;

    /** 创建统一响应。 */
    public ApiResponse(String code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    /** 创建成功响应。 */
    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>("SUCCESS", message, data);
    }

    /** 创建失败响应。 */
    public static <T> ApiResponse<T> failure(String code, String message) {
        return new ApiResponse<>(code, message, null);
    }

    /** 获取结果码。 */
    public String getCode() { return code; }

    /** 获取结果说明。 */
    public String getMessage() { return message; }

    /** 获取业务数据。 */
    public T getData() { return data; }
}
