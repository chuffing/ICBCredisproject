package com.danuvius.platform.model.enums;

import lombok.Getter;

/**
 * Redis 运行状态。
 * 数据库存数字字符串，实体/DTO 层展示文字。
 */
@Getter
public enum StatusEnum {

    /**
     * executor_info表 / sentinel_node_info表 / rms_node_status_log表
     */
    /*executorStatus / sentinelStatus / collectStatus*/
    OFFLINE("0","离线"),
    ONLINE("1","在线"),

    /*managementStatus*/
    INACTIVE("0","未纳管"),
    ACTIVE("1","已纳管"),

    /**
     * redis_instance表
     */
    /*runStatus*/
    RUN_OFFLINE("0", "离线"),
    RUN_NORMAL("1", "正常"),
    RUN_WARNING("2", "警告"),
    RUN_CRITICAL("3", "严重"),

    /**
     * redis_version_package
     */
    /*packageStatus*/
    INVALID("0","失效"),
    VALID("1","有效"),

    /**
     * alarm_rule表
     */
    /*enableStatus*/
    DISABLE("0","禁用"),
    ENABLE("1","启用"),

    /**
     * alarm_record表
     */
    /*recoverStatus*/
    UNRECOVER("0","未恢复"),
    RECOVER("1","已恢复"),

    /**
     * redis_switch_event表
     */
    /*switchResult*/
    FAILED("0", "失败"),
    SUCCESS("1", "成功");







    private final String code;
    private final String desc;

    StatusEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**数字转文字*/
    public static String descOf(String code) {
        if (code == null) {
            return null;
        }
        for (StatusEnum e : values()) {
            if (e.code.equals(code)) {
                return e.desc;
            }
        }
        // 已是文字的直接返回
        for (StatusEnum e : values()) {
            if (e.desc.equals(code)) {
                return e.desc;
            }
        }
        return code;
    }

    /**文字转数字*/
    public static String codeOf(String desc) {
        if (desc == null) {
            return null;
        }
        for (StatusEnum e : values()) {
            if (e.desc.equals(desc)) {
                return e.code;
            }
        }
        for (StatusEnum e : values()) {
            if (e.code.equals(desc)) {
                return e.code;
            }
        }
        return desc;
    }

    public static StatusEnum ofCode(String code) {
        if (code == null) return null;
        for (StatusEnum e : values()) {
            if (e.code.equals(code)) return e;
        }
        return null;
    }
}
