package com.redisops.platform.versionupgrade.entity;

import lombok.Data;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * redis_version_package 表对应的平台版本包元数据。
 */
@TableName("redis_version_package")
@Data
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
}
