package com.redisops.platform.versionupgrade.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.redisops.platform.versionupgrade.entity.RedisVersionPackage;
import com.redisops.platform.versionupgrade.model.VersionPackageData;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.List;

/**
 * Redis 版本包上传、查询和任务下发文件定位接口。
 */
public interface VersionPackageService extends IService<RedisVersionPackage> {

    /** 接收、校验并保存一个版本包；完全重复上传时返回已有记录。 */
    VersionPackageData upload(String version, String uploadedBy, MultipartFile file);

    /** 查询全部版本包，供创建版本升级任务时选择。 */
    List<VersionPackageData> listPackages();

    /** 根据主键读取一条已经校验通过的版本包。 */
    RedisVersionPackage requireAvailable(Long packageId);

    /** 根据任务参数定位平台需要发送给 Agent 的原始 tar.gz 文件。 */
    Path requirePackageFile(String version, String packageSha256);
}
