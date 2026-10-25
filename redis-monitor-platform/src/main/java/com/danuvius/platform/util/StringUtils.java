package com.danuvius.platform.util;

/**
 * 字符串处理工具类。
 */
public final class StringUtils {

    /** 工具类不允许实例化。 */
    private StringUtils() {
    }

    /**
     * 去除字符串首尾空格，空字符串统一返回null。
     *
     * @param value 待处理的字符串
     * @return 处理后的字符串；原值为空或仅包含空格时返回null
     */
    public static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
