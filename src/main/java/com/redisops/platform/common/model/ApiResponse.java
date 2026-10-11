package com.redisops.platform.common.model;

import lombok.Data;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 平台提供给执行器的统一响应结构。
 *
 * @param <T> data 字段类型
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
public class ApiResponse<T> {

    private final String code;
    private final String message;
    private final T data;

    /** 创建成功响应。 */
    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>("SUCCESS", message, data);
    }

    /** 创建失败响应。 */
    public static <T> ApiResponse<T> failure(String code, String message) {
        return new ApiResponse<>(code, message, null);
    }
}
