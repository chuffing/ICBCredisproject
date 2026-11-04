package com.danuvius.platform.util;

import com.danuvius.platform.common.constant.AgentTaskConstants;
import com.danuvius.platform.common.exception.BusinessCode;
import com.danuvius.platform.common.exception.BusinessException;
import org.apache.commons.lang3.StringUtils;

/**
 * Authorization 请求头解析
 */
public final class AuthTokenUtil {

    private AuthTokenUtil() {
    }

    /**
     * 去掉 Bearer 前缀并去除首尾空白；未带前缀时原样返回
     *
     * @param authorization 原始 Authorization 请求头，可为 null
     * @return 去除前缀后的 token，空白返回 null
     */
    public static String stripBearer(String authorization) {
        if (authorization == null) {
            return null;
        }
        String value = authorization.trim();
        if (value.startsWith(AgentTaskConstants.BEARER_PREFIX)) {
            value = value.substring(AgentTaskConstants.BEARER_PREFIX.length());
        }
        return StringUtils.trimToNull(value);
    }

    /**
     * 严格解析 Bearer Token：前缀缺失或 token 为空时抛业务异常
     *
     * @param authorization 原始 Authorization 请求头
     * @return 有效 token
     */
    public static String extractBearer(String authorization) {
        if (authorization == null || !authorization.trim().startsWith(AgentTaskConstants.BEARER_PREFIX)) {
            throw new BusinessException(
                    BusinessCode.NO_AUTH_ERROR.getCode(),
                    "执行器认证信息格式不正确"
            );
        }
        String token = stripBearer(authorization);
        if (token == null) {
            throw new BusinessException(
                    BusinessCode.NO_AUTH_ERROR.getCode(),
                    "执行器Token不能为空"
            );
        }
        return token;
    }
}
