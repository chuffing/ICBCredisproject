package com.danuvius.platform.model.enums.instance;

import lombok.Getter;

@Getter
public enum SwitchTypeEnum {


    /**
     * redis_switch_event表
     */
    /*switchType*/
    INIT("0", "基线"),
    AUTO("1", "自动切换"),
    MANUAL("2", "手动切换"),
    OTHER("3", "其他");




    private final String code;
    private final String desc;

    SwitchTypeEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**数字转文字*/
    public static String descOf(String code) {
        if (code == null) {
            return null;
        }
        for (SwitchTypeEnum e : values()) {
            if (e.code.equals(code)) {
                return e.desc;
            }
        }
        // 已是文字的直接返回
        for (SwitchTypeEnum e : values()) {
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
        for (SwitchTypeEnum e : values()) {
            if (e.desc.equals(desc)) {
                return e.code;
            }
        }
        for (SwitchTypeEnum e : values()) {
            if (e.code.equals(desc)) {
                return e.code;
            }
        }
        return desc;
    }

    public static SwitchTypeEnum ofCode(String code) {
        if (code == null) return null;
        for (SwitchTypeEnum e : values()) {
            if (e.code.equals(code)) return e;
        }
        return null;
    }

}
