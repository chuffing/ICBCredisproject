package com.danuvius.platform.common.constant;

/**
 * 报告导出任务 ID、下载地址与临时文件参数 常量
 */
public final class ReportConstants {

    /** 导出任务 ID 前缀，完整格式为 EP_ + 18~19 位雪花数字 */
    public static final String EXPORT_TASK_ID_PREFIX = "EP_";

    /** 导出任务 ID 最大长度（前缀 + 雪花 ID） */
    public static final int EXPORT_TASK_ID_MAX_LEN = 32;

    /** 导出文件下载地址模板，后接任务 ID */
    public static final String EXPORT_DOWNLOAD_URL_TEMPLATE = "/monitor/report/export/download?taskId=";

    /** 导出文件内容类型 */
    public static final String MIME_PDF = "application/pdf";

    /** 导出任务与临时文件有效期（分钟） */
    public static final long EXPORT_TASK_TTL_MINUTES = 30L;

    /** 导出任务缓存最大条数 */
    public static final long EXPORT_TASK_CACHE_MAX = 500L;

    private ReportConstants() {
    }
}
