package com.danuvius.platform.model.enums.instance;

import lombok.Getter;

@Getter
public enum ManagementStatusEnum {
    /*managementStatus*/
    INACTIVE("0","未纳管"),
    ACTIVE("1","已纳管");

    private final String code;
    private final String desc;

    ManagementStatusEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**数字转文字*/
    public static String descOf(String code) {
        if (code == null) {
            return null;
        }
        for (ManagementStatusEnum e : values()) {
            if (e.code.equals(code)) {
                return e.desc;
            }
        }
        // 已是文字的直接返回
        for (ManagementStatusEnum e : values()) {
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
        for (ManagementStatusEnum e : values()) {
            if (e.desc.equals(desc)) {
                return e.code;
            }
        }
        for (ManagementStatusEnum e : values()) {
            if (e.code.equals(desc)) {
                return e.code;
            }
        }
        return desc;
    }

    public static ManagementStatusEnum ofCode(String code) {
        if (code == null) return null;
        for (ManagementStatusEnum e : values()) {
            if (e.code.equals(code)) return e;
        }
        return null;
    }
}
