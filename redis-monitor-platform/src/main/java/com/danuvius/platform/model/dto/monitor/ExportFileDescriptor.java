package com.danuvius.platform.model.dto.monitor;

import lombok.Data;

import java.io.File;
import java.io.Serializable;

/**
 * 报告导出文件描述
 * 服务层返回给控制层，控制层只负责写 HTTP 响应，不直接依赖任务内部结构
 */
@Data
public class ExportFileDescriptor implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 临时文件 */
    private File file;

    /** 下载文件名 */
    private String fileName;

    /** 内容类型，如 application/pdf */
    private String contentType;

    public ExportFileDescriptor() {
    }

    public ExportFileDescriptor(File file, String fileName, String contentType) {
        this.file = file;
        this.fileName = fileName;
        this.contentType = contentType;
    }
}
