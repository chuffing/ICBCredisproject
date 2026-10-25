package com.danuvius.platform.common.constant;

import java.util.regex.Pattern;

/**
 * 通用参数校验常量。
 */
public final class ValidationConstants {

    /**
     * 工具类不允许实例化。
     */
    private ValidationConstants() {
    }

    /**
     * 操作原因最大字符数。
     */
    public static final int MAX_OPERATION_REASON_LENGTH = 500;

    /**
     * 操作人最大字符数。
     */
    public static final int MAX_CREATED_BY_LENGTH = 64;

    /**
     * 安装包文件名最大字符数。
     */
    public static final int MAX_PACKAGE_FILE_NAME_LENGTH = 128;

    /**
     * Redis源码安装包文件名格式。
     */
    public static final Pattern REDIS_PACKAGE_FILE_NAME_PATTERN =
            Pattern.compile(
                    "^redis-([0-9]+(?:\\.[0-9]+){1,3}"
                            + "(?:-[0-9A-Za-z.]+)?)\\.tar\\.gz$"
            );

    /**
     * SHA-256摘要格式。
     */
    public static final Pattern SHA256_PATTERN =
            Pattern.compile("[0-9a-fA-F]{64}");

    /**
     * 运维任务编号格式。
     */
    public static final Pattern TASK_NO_PATTERN =
            Pattern.compile("TASK\\d{12}");
}
