package com.redisops.platform.versionupgrade.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * redis_version_package 表对应的平台版本包元数据。
 */
@TableName("redis_version_package")
public class RedisVersionPackage {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String packageName;
    private String declaredVersion;
    private String detectedVersion;
    private String storagePath;
    private String packageHash;
    private Long packageSize;
    private String verifyStatus;
    private String verifyMessage;
    private String uploadedBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /** 获取主键。 */ public Long getId() { return id; }
    /** 设置主键。 */ public void setId(Long id) { this.id = id; }
    /** 获取用户上传时的原始文件名。 */ public String getPackageName() { return packageName; }
    /** 设置用户上传时的原始文件名。 */ public void setPackageName(String packageName) { this.packageName = packageName; }
    /** 获取用户声明的 Redis 版本号。 */ public String getDeclaredVersion() { return declaredVersion; }
    /** 设置用户声明的 Redis 版本号。 */ public void setDeclaredVersion(String declaredVersion) { this.declaredVersion = declaredVersion; }
    /** 获取检测到的 Redis 版本号。 */ public String getDetectedVersion() { return detectedVersion; }
    /** 设置检测到的 Redis 版本号。 */ public void setDetectedVersion(String detectedVersion) { this.detectedVersion = detectedVersion; }
    /** 获取平台内部存储路径。 */ public String getStoragePath() { return storagePath; }
    /** 设置平台内部存储路径。 */ public void setStoragePath(String storagePath) { this.storagePath = storagePath; }
    /** 获取原始 tar.gz 文件 SHA-256。 */ public String getPackageHash() { return packageHash; }
    /** 设置原始 tar.gz 文件 SHA-256。 */ public void setPackageHash(String packageHash) { this.packageHash = packageHash; }
    /** 获取原始文件字节数。 */ public Long getPackageSize() { return packageSize; }
    /** 设置原始文件字节数。 */ public void setPackageSize(Long packageSize) { this.packageSize = packageSize; }
    /** 获取校验状态。 */ public String getVerifyStatus() { return verifyStatus; }
    /** 设置校验状态。 */ public void setVerifyStatus(String verifyStatus) { this.verifyStatus = verifyStatus; }
    /** 获取校验说明。 */ public String getVerifyMessage() { return verifyMessage; }
    /** 设置校验说明。 */ public void setVerifyMessage(String verifyMessage) { this.verifyMessage = verifyMessage; }
    /** 获取上传人。 */ public String getUploadedBy() { return uploadedBy; }
    /** 设置上传人。 */ public void setUploadedBy(String uploadedBy) { this.uploadedBy = uploadedBy; }
    /** 获取创建时间。 */ public LocalDateTime getCreateTime() { return createTime; }
    /** 设置创建时间。 */ public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    /** 获取更新时间。 */ public LocalDateTime getUpdateTime() { return updateTime; }
    /** 设置更新时间。 */ public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
