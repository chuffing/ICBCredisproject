package com.redisops.platform.versionupgrade.model;

import lombok.Data;
import com.redisops.platform.versionupgrade.entity.RedisVersionPackage;

import java.time.LocalDateTime;

/**
 * 返回给前端的版本包信息，不暴露平台服务器上的真实存储路径。
 */
@Data
public class VersionPackageData {

    private final Long id;
    private final String packageName;
    private final String version;
    private final String packageSha256;
    private final Long packageSize;
    private final String verifyStatus;
    private final String verifyMessage;
    private final String uploadedBy;
    private final LocalDateTime createTime;
    private final boolean reusedExisting;

    /** 根据数据库实体生成安全的接口返回对象。 */
    public VersionPackageData(RedisVersionPackage entity) {
        this(entity, false);
    }

    /** 根据数据库实体生成接口返回对象，并标记本次是否复用了已有包。 */
    public VersionPackageData(RedisVersionPackage entity, boolean reusedExisting) {
        this.id = entity.getId();
        this.packageName = entity.getPackageName();
        this.version = entity.getDeclaredVersion();
        this.packageSha256 = entity.getPackageHash();
        this.packageSize = entity.getPackageSize();
        this.verifyStatus = entity.getVerifyStatus();
        this.verifyMessage = entity.getVerifyMessage();
        this.uploadedBy = entity.getUploadedBy();
        this.createTime = entity.getCreateTime();
        this.reusedExisting = reusedExisting;
    }
}
