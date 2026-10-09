package com.redisops.platform.operation.type;

/**
 * 平台可以下发给执行器的原子操作白名单。
 */
public enum AgentOperationType {
    PRECHECK_HOST,
    START_REDIS,
    STOP_REDIS,
    START_SENTINEL,
    STOP_SENTINEL,
    RESTORE_CONFIG,
    DEPLOY_CONFIG,
    DEPLOY_VERSION,
    SWITCH_VERSION,
    ROLLBACK_VERSION;

    /** 将请求字符串转换为白名单中的原子操作。 */
    public static AgentOperationType fromValue(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("operationType 不能为空");
        }
        try {
            return valueOf(value.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("不支持的 operationType: " + value, e);
        }
    }
}
