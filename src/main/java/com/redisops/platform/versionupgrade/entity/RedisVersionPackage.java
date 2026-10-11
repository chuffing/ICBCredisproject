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

    /** 数据库自增主键，唯一标识一个版本包。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户上传的版本包文件名，保存前会去除路径等不安全内容。 */
    private String packageName;

    /** 上传人填写的 Redis 版本号。 */
    private String declaredVersion;

    /** 实际检测到的 Redis 版本号；当前平台上传阶段不检测，暂存为空。 */
    private String detectedVersion;

    /** 版本包在平台服务器上的存储路径。 */
    private String storagePath;

    /** 版本包文件的 SHA-256 摘要，用于完整性校验和重复包判断。 */
    private String packageHash;

    /** 版本包文件大小，单位为字节。 */
    private Long packageSize;

    /** 平台校验状态：PENDING 待校验、PASSED 通过、FAILED 失败。 */
    private String verifyStatus;

    /** 版本包校验结果的说明信息。 */
    private String verifyMessage;

    /** 上传人标识，由上传请求提供。 */
    private String uploadedBy;

    /** 版本包记录创建时间，由数据库自动生成。 */
    private LocalDateTime createTime;

    /** 版本包记录最后更新时间，由数据库自动维护。 */
    private LocalDateTime updateTime;
}
