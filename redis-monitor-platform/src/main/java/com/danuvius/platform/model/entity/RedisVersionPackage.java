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

    /** 数据库内部主键。 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 上传时的安装包文件名。 */
    private String packageName;

    /** 从文件名及源码中校验得到的Redis版本号。 */
    private String redisVersion;

    /** 安装包在平台服务器上的存储路径。 */
    private String storagePath;

    /** 安装包文件的SHA-256摘要。 */
    private String sha256;

    /** 安装包文件大小，单位为字节。 */
    private Long packageSize;

    /** 安装包状态：ENABLED或DISABLED。 */
    private String packageStatus;

    /** 上传操作人。 */
    private String uploadedBy;

    /** 创建时间。 */
    private LocalDateTime createdTime;

    /** 更新时间。 */
    private LocalDateTime updatedTime;
}
