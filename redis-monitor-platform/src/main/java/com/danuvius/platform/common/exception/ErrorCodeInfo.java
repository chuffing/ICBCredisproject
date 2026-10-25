package com.danuvius.platform.common.exception;

/**
 * 统一响应码定义。
 */
public interface ErrorCodeInfo {

    /**
     * 获取响应码。
     */
    int getCode();

    /**
     * 获取响应信息。
     */
    String getMsg();
}
