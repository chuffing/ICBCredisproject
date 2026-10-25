package com.danuvius.platform.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Redis源码版本安装包。
 */
@Data
@TableName("redis_version_package")
public class RedisVersionPackage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String packageName;

    private String redisVersion;

    private String storagePath;

    private String sha256;

    private Long packageSize;

    private String packageStatus;

    private String uploadedBy;

    private LocalDateTime createdTime;

    private LocalDateTime updatedTime;
}
