package com.danuvius.platform.model.enums.metric;

import lombok.Getter;

@Getter
public enum CollectTypeEnum {
    /**
     * rms_metric_log表
     */
    /*collectType*/
    AUTO_COLLECT("0","auto_collect"),
    MANUAL_COLLECT("1","manual_collect");

    private final String code;
    private final String desc;

    CollectTypeEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**数字转文字*/
    public static String descOf(String code) {
        if (code == null) {
            return null;
        }
        for (CollectTypeEnum e : values()) {
            if (e.code.equals(code)) {
                return e.desc;
            }
        }
        // 已是文字的直接返回
        for (CollectTypeEnum e : values()) {
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
        for (CollectTypeEnum e : values()) {
            if (e.desc.equals(desc)) {
                return e.code;
            }
        }
        for (CollectTypeEnum e : values()) {
            if (e.code.equals(desc)) {
                return e.code;
            }
        }
        return desc;
    }

    public static CollectTypeEnum ofCode(String code) {
        if (code == null) return null;
        for (CollectTypeEnum e : values()) {
            if (e.code.equals(code)) return e;
        }
        return null;
    }
}
