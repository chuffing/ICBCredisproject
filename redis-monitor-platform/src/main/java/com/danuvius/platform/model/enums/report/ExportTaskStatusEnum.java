package com.danuvius.platform.model.enums.report;

import lombok.Getter;

/**
 * 报告导出任务状态
 */
@Getter
public enum ExportTaskStatusEnum {

    /** 任务已创建，异步生成中 */
    PENDING("pending", "处理中"),
    /** 导出成功 */
    SUCCESS("success", "成功"),
    /** 导出失败 */
    FAIL("fail", "失败");

    private final String code;
    private final String desc;

    ExportTaskStatusEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }


    /** 判断给定状态码是否为成功 */
    public static boolean isSuccess(String code) {
        return SUCCESS.code.equals(code);
    }
}
