package com.danuvius.platform.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Redis版本安装包上传结果。
 */
@Data
public class VersionPackageUploadVO {

    /** 安装包记录编号。 */
    private Long packageId;

    /** Redis源码安装包文件名。 */
    private String packageName;

    /** 安装包对应的Redis版本。 */
    private String redisVersion;

    /** 安装包文件的SHA-256摘要。 */
    private String sha256;

    /** 安装包文件大小，单位为字节。 */
    private Long packageSize;

    /** 安装包启用状态。 */
    private String packageStatus;

    /** 安装包上传人。 */
    private String uploadedBy;

    /** 安装包上传时间。 */
    private LocalDateTime createdTime;

    /** 本次上传是否复用了已经存在的相同安装包。 */
    private boolean reusedExisting;
}
