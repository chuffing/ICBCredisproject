package com.danuvius.platform.model.enums.instance;

import lombok.Getter;

@Getter
public enum RoleTypeEnum {
    /**
     * redis_instance表
     */
    /*role*/
    MASTER("0","MASTER"),
    REPLICA("1","REPLICA"),
    UNKNOWN("2","UNKNOWN");

    private final String code;
    private final String desc;

    RoleTypeEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**数字转文字*/
    public static String descOf(String code) {
        if (code == null) {
            return null;
        }
        for (RoleTypeEnum e : values()) {
            if (e.code.equals(code)) {
                return e.desc;
            }
        }
        // 已是文字的直接返回
        for (RoleTypeEnum e : values()) {
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
        for (RoleTypeEnum e : values()) {
            if (e.desc.equals(desc)) {
                return e.code;
            }
        }
        for (RoleTypeEnum e : values()) {
            if (e.code.equals(desc)) {
                return e.code;
            }
        }
        return desc;
    }

    public static RoleTypeEnum ofCode(String code) {
        if (code == null) return null;
        for (RoleTypeEnum e : values()) {
            if (e.code.equals(code)) return e;
        }
        return null;
    }
}
