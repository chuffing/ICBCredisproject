package com.redisops.platform.operation.type;

/**
 * 平台允许创建的三类运维业务任务。
 */
public enum BusinessTaskType {
    FAULT_RECOVERY,
    VERSION_UPGRADE,
    SERVER_SWITCH;

    /** 将请求字符串转换为固定业务任务类型。 */
    public static BusinessTaskType fromValue(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("taskType 不能为空");
        }
        try {
            return valueOf(value.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("不支持的 taskType: " + value, e);
        }
    }
}
