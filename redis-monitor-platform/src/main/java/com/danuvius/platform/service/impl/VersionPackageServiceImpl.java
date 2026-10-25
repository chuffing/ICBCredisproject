package com.danuvius.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.danuvius.platform.common.constant.ValidationConstants;
import com.danuvius.platform.common.constant.VersionPackageConstants;
import com.danuvius.platform.common.exception.BusinessCode;
import com.danuvius.platform.common.exception.BusinessException;
import com.danuvius.platform.common.exception.ParameterErrorCode;
import com.danuvius.platform.common.exception.ParameterException;
import com.danuvius.platform.mapper.RedisVersionPackageMapper;
import com.danuvius.platform.model.entity.RedisVersionPackage;
import com.danuvius.platform.model.vo.VersionPackageItemVO;
import com.danuvius.platform.model.vo.VersionPackageUploadVO;
import com.danuvius.platform.service.VersionPackageService;
import com.danuvius.platform.util.Sha256Util;
import lombok.RequiredArgsConstructor;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Redis版本安装包上传服务实现。
 */
@Service
@RequiredArgsConstructor
public class VersionPackageServiceImpl implements VersionPackageService {

    private static final int BUFFER_SIZE = 8192;

    private static final ZoneId PLATFORM_ZONE_ID =
            ZoneId.of("Asia/Shanghai");

    private static final Pattern REDIS_VERSION_DEFINE_PATTERN =
            Pattern.compile(
                    "(?m)^\\s*#define\\s+REDIS_VERSION\\s+"
                            + "\"([^\"]+)\"\\s*$"
            );

    private final RedisVersionPackageMapper versionPackageMapper;

    /** 安装包持久化目录。 */
    @Value("${platform.version-package.storage-dir:./data/versionPackages}")
    private String storageDir;

    /** 单个上传文件最大字节数。 */
    @Value("${platform.version-package.max-bytes:52428800}")
    private long maxBytes;

    /**
     * 上传Redis源码安装包。
     *
     * @param uploadedBy 上传操作人
     * @param file Redis源码安装包
     * @return 上传结果
     */
    @Override
    public synchronized VersionPackageUploadVO upload(
            String uploadedBy,
            MultipartFile file) {

        // 1. 校验文件名和页面上报的文件大小，并从文件名中提取Redis版本。
        String packageName = validatePackageFile(file);
        String fileNameVersion = extractVersion(packageName);

        // 2. 准备安装包存储目录，并在目录中创建临时文件。
        Path storageDirectory = prepareStorageDirectory();
        Path temporaryFile = null;
        Path storedFile = null;
        boolean storedFileCreated = false;

        try {
            temporaryFile = Files.createTempFile(
                    storageDirectory,
                    ".upload-",
                    VersionPackageConstants.PACKAGE_FILE_SUFFIX
            );

            // 3. 流式写入临时文件，同时计算SHA-256并限制实际文件大小。
            String sha256 = saveTemporaryFile(file, temporaryFile);
            long packageSize = Files.size(temporaryFile);

            // 4. 检查压缩包目录和源码文件，并读取src/version.h中的真实版本。
            String internalVersion = inspectArchive(temporaryFile);
            if (!fileNameVersion.equals(internalVersion)) {
                throw new ParameterException(
                        ParameterErrorCode.PACKAGE_FILE_INVALID,
                        "文件名版本" + fileNameVersion
                                + "与src/version.h中的版本"
                                + internalVersion + "不一致"
                );
            }

            // 5. 相同文件已经上传时，直接返回原记录，不重复保存文件和数据库记录。
            RedisVersionPackage sameFile = versionPackageMapper.selectOne(
                    new LambdaQueryWrapper<RedisVersionPackage>()
                            .eq(RedisVersionPackage::getSha256, sha256)
                            .last("LIMIT 1")
            );
            if (sameFile != null) {
                if (!fileNameVersion.equals(sameFile.getRedisVersion())) {
                    throw new BusinessException(
                            BusinessCode.OPERATION_ERROR.getCode(),
                            "相同文件摘要已登记为其他Redis版本"
                    );
                }
                return buildUploadResult(sameFile, true);
            }

            // 6. 同一Redis版本只能保存一个安装包，版本相同但摘要不同时拒绝上传。
            RedisVersionPackage sameVersion = versionPackageMapper.selectOne(
                    new LambdaQueryWrapper<RedisVersionPackage>()
                            .eq(
                                    RedisVersionPackage::getRedisVersion,
                                    fileNameVersion
                            )
                            .last("LIMIT 1")
            );
            if (sameVersion != null) {
                throw new BusinessException(
                        BusinessCode.OPERATION_ERROR.getCode(),
                        "Redis版本" + fileNameVersion
                                + "已存在，但安装包摘要不同"
                );
            }

            // 7. 使用校验通过的原始文件名，将临时文件移动到正式存储位置。
            storedFile = storageDirectory.resolve(packageName).normalize();
            moveToStorage(temporaryFile, storedFile);
            temporaryFile = null;
            storedFileCreated = true;

            // 8. 保存安装包记录。上传后的安装包默认可用。
            LocalDateTime currentTime = LocalDateTime.now(PLATFORM_ZONE_ID);
            RedisVersionPackage versionPackage = new RedisVersionPackage();
            versionPackage.setPackageName(packageName);
            versionPackage.setRedisVersion(fileNameVersion);
            versionPackage.setStoragePath(storedFile.toString());
            versionPackage.setSha256(sha256);
            versionPackage.setPackageSize(packageSize);
            versionPackage.setPackageStatus(
                    VersionPackageConstants.DEFAULT_PACKAGE_STATUS
            );
            versionPackage.setUploadedBy(uploadedBy);
            versionPackage.setCreatedTime(currentTime);
            versionPackage.setUpdatedTime(currentTime);

            int insertedRows = versionPackageMapper.insert(versionPackage);
            if (insertedRows != 1) {
                throw new BusinessException(
                        BusinessCode.OPERATION_ERROR.getCode(),
                        "保存Redis安装包记录失败"
                );
            }

            // 9. 返回上传成功后的安装包信息。
            return buildUploadResult(versionPackage, false);
        } catch (IOException exception) {
            if (storedFileCreated) {
                deleteFileQuietly(storedFile);
            }
            throw new BusinessException(
                    BusinessCode.OPERATION_ERROR.getCode(),
                    "处理Redis安装包失败：" + exception.getMessage()
            );
        } catch (RuntimeException exception) {
            if (storedFileCreated) {
                deleteFileQuietly(storedFile);
            }
            throw exception;
        } finally {
            // 无论上传成功还是失败，都清理仍然存在的临时文件。
            deleteFileQuietly(temporaryFile);
        }
    }

    /**
     * 查询全部Redis版本安装包。
     *
     * @return 安装包列表
     */
    @Override
    public List<VersionPackageItemVO> listPackages() {

        // 1. 按上传时间倒序查询全部安装包记录。
        List<RedisVersionPackage> versionPackages =
                versionPackageMapper.selectList(
                        new LambdaQueryWrapper<RedisVersionPackage>()
                                .orderByDesc(
                                        RedisVersionPackage::getCreatedTime
                                )
                );

        // 2. 将数据库实体转换为页面需要的列表项。
        List<VersionPackageItemVO> results = new ArrayList<>();
        if (versionPackages != null) {
            for (RedisVersionPackage versionPackage : versionPackages) {
                VersionPackageItemVO item = new VersionPackageItemVO();
                item.setPackageId(versionPackage.getId());
                item.setPackageName(versionPackage.getPackageName());
                item.setRedisVersion(versionPackage.getRedisVersion());
                item.setSha256(versionPackage.getSha256());
                item.setPackageSize(versionPackage.getPackageSize());
                item.setPackageStatus(versionPackage.getPackageStatus());
                item.setUploadedBy(versionPackage.getUploadedBy());
                item.setCreatedTime(versionPackage.getCreatedTime());
                results.add(item);
            }
        }

        // 3. 返回安装包列表。
        return results;
    }

    /**
     * 校验上传文件并返回原始文件名。
     */
    private String validatePackageFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ParameterException(
                    ParameterErrorCode.PACKAGE_FILE_INVALID,
                    "安装包文件不能为空"
            );
        }

        String packageName = file.getOriginalFilename();
        if (packageName == null || !packageName.equals(packageName.trim())) {
            throw new ParameterException(
                    ParameterErrorCode.PACKAGE_FILE_INVALID,
                    "安装包文件名不能为空且不能包含首尾空格"
            );
        }
        if (packageName.length()
                > ValidationConstants.MAX_PACKAGE_FILE_NAME_LENGTH) {
            throw new ParameterException(
                    ParameterErrorCode.PACKAGE_FILE_INVALID,
                    "安装包文件名过长"
            );
        }
        if (packageName.contains("/") || packageName.contains("\\")
                || packageName.contains("..")) {
            throw new ParameterException(
                    ParameterErrorCode.PACKAGE_FILE_INVALID,
                    "安装包文件名包含非法路径字符"
            );
        }
        if (!ValidationConstants.REDIS_PACKAGE_FILE_NAME_PATTERN
                .matcher(packageName).matches()) {
            throw new ParameterException(
                    ParameterErrorCode.PACKAGE_FILE_INVALID,
                    "安装包文件名必须符合redis-{version}.tar.gz"
            );
        }
        if (file.getSize() <= 0 || file.getSize() > maxBytes) {
            throw new ParameterException(
                    ParameterErrorCode.PACKAGE_FILE_INVALID,
                    "安装包大小必须大于0且不能超过"
                            + maxBytes + "字节"
            );
        }
        return packageName;
    }

    /**
     * 从安装包文件名中提取Redis版本。
     */
    private String extractVersion(String packageName) {
        Matcher matcher = ValidationConstants.REDIS_PACKAGE_FILE_NAME_PATTERN
                .matcher(packageName);
        if (!matcher.matches()) {
            throw new ParameterException(
                    ParameterErrorCode.PACKAGE_FILE_INVALID,
                    "无法从安装包文件名提取Redis版本"
            );
        }
        return matcher.group(1);
    }

    /**
     * 准备安装包存储目录。
     */
    private Path prepareStorageDirectory() {

        String configuredPath = storageDir;
        if (configuredPath == null || configuredPath.trim().isEmpty()) {
            throw new BusinessException(
                    BusinessCode.OPERATION_ERROR.getCode(),
                    "Redis安装包存储目录未配置"
            );
        }

        try {
            Path storageDirectory = Paths.get(configuredPath.trim())
                    .toAbsolutePath().normalize();
            Files.createDirectories(storageDirectory);
            if (!Files.isDirectory(storageDirectory)) {
                throw new BusinessException(
                        BusinessCode.OPERATION_ERROR.getCode(),
                        "Redis安装包存储路径不是目录"
                );
            }
            return storageDirectory.toRealPath();
        } catch (IOException exception) {
            throw new BusinessException(
                    BusinessCode.OPERATION_ERROR.getCode(),
                    "无法准备Redis安装包存储目录："
                            + exception.getMessage()
            );
        }
    }

    /**
     * 流式保存上传文件并计算SHA-256。
     */
    private String saveTemporaryFile(
            MultipartFile file,
            Path temporaryFile) throws IOException {

        long actualFileSize = 0L;
        byte[] buffer = new byte[BUFFER_SIZE];

        try (DigestInputStream inputStream = Sha256Util.createInputStream(
                file.getInputStream()
        ); OutputStream outputStream = Files.newOutputStream(temporaryFile)) {
            int readBytes;
            while ((readBytes = inputStream.read(buffer)) != -1) {
                actualFileSize += readBytes;
                if (actualFileSize > maxBytes) {
                    throw new ParameterException(
                            ParameterErrorCode.PACKAGE_FILE_INVALID,
                            "安装包实际大小超过限制"
                    );
                }
                outputStream.write(buffer, 0, readBytes);
            }

            if (actualFileSize <= 0) {
                throw new ParameterException(
                        ParameterErrorCode.PACKAGE_FILE_INVALID,
                        "安装包内容不能为空"
                );
            }

            return Sha256Util.getDigest(inputStream);
        }
    }

    /**
     * 检查tar.gz源码包结构，并返回src/version.h中的Redis版本。
     * 主要进行以下检查：
     * 1. 压缩包中的文件数量是否超过限制。
     * 2. 文件路径是否合法，是否包含重复路径。
     * 3. 是否只有一个Redis源码根目录。
     * 4. 是否存在不允许的特殊文件。
     * 5. 解压后的文件总大小是否超过限制。
     * 6. 是否存在必要的Redis源码文件。
     * 7. 能否从src/version.h中获取Redis版本号。
     * 8. 源码根目录名称是否与内部版本号一致。
     */
    private String inspectArchive(Path archive) {
        Set<String> archiveEntries = new HashSet<>();
        String rootDirectory = null;
        String internalVersion = null;
        boolean makefileFound = false;
        boolean serverSourceFound = false;
        boolean redisCliSourceFound = false;
        int entryCount = 0;
        long uncompressedSize = 0L;

        try (InputStream fileInputStream = Files.newInputStream(archive);
             GzipCompressorInputStream gzipInputStream =
                     new GzipCompressorInputStream(fileInputStream);
             TarArchiveInputStream tarInputStream =
                     new TarArchiveInputStream(gzipInputStream)) {

            TarArchiveEntry archiveEntry;
            while ((archiveEntry = tarInputStream.getNextTarEntry()) != null) {
                // 1. 限制压缩包中的文件数量。
                entryCount++;
                if (entryCount
                        > VersionPackageConstants.MAX_ARCHIVE_ENTRY_COUNT) {
                    throw new ParameterException(
                            ParameterErrorCode.PACKAGE_FILE_INVALID,
                            "安装包内文件数量超过限制"
                    );
                }

                // 2. 校验路径，禁止重复路径、绝对路径和目录穿越。
                String entryName = normalizeArchiveEntryName(
                        archiveEntry.getName()
                );
                if (!archiveEntries.add(entryName)) {
                    throw new ParameterException(
                            ParameterErrorCode.PACKAGE_FILE_INVALID,
                            "安装包包含重复路径：" + entryName
                    );
                }

                // 3. 确认压缩包中只有一个redis-*源码根目录。
                String entryRoot = entryName;
                int separatorIndex = entryName.indexOf('/');
                if (separatorIndex >= 0) {
                    entryRoot = entryName.substring(0, separatorIndex);
                }
                if (!entryRoot.startsWith("redis-")) {
                    throw new ParameterException(
                            ParameterErrorCode.PACKAGE_FILE_INVALID,
                            "Redis源码根目录必须以redis-开头"
                    );
                }
                if (rootDirectory == null) {
                    rootDirectory = entryRoot;
                } else if (!rootDirectory.equals(entryRoot)) {
                    throw new ParameterException(
                            ParameterErrorCode.PACKAGE_FILE_INVALID,
                            "安装包必须只有一个Redis源码根目录"
                    );
                }

                // 4. 禁止符号链接、硬链接和设备文件。
                if (archiveEntry.isSymbolicLink()
                        || archiveEntry.isLink()
                        || archiveEntry.isCharacterDevice()
                        || archiveEntry.isBlockDevice()
                        || archiveEntry.isFIFO()) {
                    throw new ParameterException(
                            ParameterErrorCode.PACKAGE_FILE_INVALID,
                            "安装包包含不允许的特殊文件"
                    );
                }

                // 5. 根据Tar头中的文件大小限制解压后的总体积。
                if (!archiveEntry.isDirectory()) {
                    if (archiveEntry.getSize() < 0) {
                        throw new ParameterException(
                                ParameterErrorCode.PACKAGE_FILE_INVALID,
                                "安装包文件大小不合法"
                        );
                    }
                    uncompressedSize += archiveEntry.getSize();
                    if (uncompressedSize
                            > VersionPackageConstants.MAX_UNCOMPRESSED_BYTES) {
                        throw new ParameterException(
                                ParameterErrorCode.PACKAGE_FILE_INVALID,
                                "安装包解压后大小超过限制"
                        );
                    }
                }

                // 6. 记录编译需要的源码文件，并读取内部Redis版本。
                if ((rootDirectory + "/Makefile").equals(entryName)) {
                    makefileFound = true;
                } else if ((rootDirectory + "/src/server.c")
                        .equals(entryName)) {
                    serverSourceFound = true;
                } else if ((rootDirectory + "/src/redis-cli.c")
                        .equals(entryName)) {
                    redisCliSourceFound = true;
                } else if ((rootDirectory + "/src/version.h")
                        .equals(entryName)) {
                    internalVersion = readInternalVersion(
                            tarInputStream,
                            archiveEntry
                    );
                }
            }
        } catch (ParameterException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new ParameterException(
                    ParameterErrorCode.PACKAGE_FILE_INVALID,
                    "安装包不是有效的tar.gz文件"
            );
        }

        // 7. 检查Redis编译所需的主要源码文件。
        if (!makefileFound || !serverSourceFound || !redisCliSourceFound) {
            throw new ParameterException(
                    ParameterErrorCode.PACKAGE_FILE_INVALID,
                    "安装包缺少Makefile、src/server.c或src/redis-cli.c"
            );
        }
        if (internalVersion == null) {
            throw new ParameterException(
                    ParameterErrorCode.PACKAGE_FILE_INVALID,
                    "无法从src/version.h识别Redis版本"
            );
        }

        // 8. Redis源码根目录版本必须与version.h中的版本一致。
        String expectedRootDirectory = "redis-" + internalVersion;
        if (!expectedRootDirectory.equals(rootDirectory)) {
            throw new ParameterException(
                    ParameterErrorCode.PACKAGE_FILE_INVALID,
                    "源码根目录版本与src/version.h中的版本不一致"
            );
        }
        return internalVersion;
    }

    /**
     * 规范并校验压缩包条目路径。
     */
    private String normalizeArchiveEntryName(String rawEntryName) {
        if (rawEntryName == null || rawEntryName.trim().isEmpty()) {
            throw new ParameterException(
                    ParameterErrorCode.PACKAGE_FILE_INVALID,
                    "安装包包含空路径"
            );
        }

        String entryName = rawEntryName.replace('\\', '/');
        while (entryName.endsWith("/")) {
            entryName = entryName.substring(0, entryName.length() - 1);
        }
        if (entryName.isEmpty()
                || entryName.startsWith("/")
                || entryName.contains(":")) {
            throw new ParameterException(
                    ParameterErrorCode.PACKAGE_FILE_INVALID,
                    "安装包包含绝对路径"
            );
        }

        Path normalizedPath = Paths.get(entryName).normalize();
        String normalizedEntryName = normalizedPath.toString()
                .replace('\\', '/');
        if (normalizedEntryName.equals("..")
                || normalizedEntryName.startsWith("../")
                || !normalizedEntryName.equals(entryName)) {
            throw new ParameterException(
                    ParameterErrorCode.PACKAGE_FILE_INVALID,
                    "安装包包含不安全路径：" + rawEntryName
            );
        }
        return normalizedEntryName;
    }

    /**
     * 从src/version.h中读取REDIS_VERSION。
     */
    private String readInternalVersion(
            TarArchiveInputStream tarInputStream,
            TarArchiveEntry archiveEntry) throws IOException {

        long versionFileSize = archiveEntry.getSize();
        if (versionFileSize <= 0
                || versionFileSize
                > VersionPackageConstants.MAX_VERSION_FILE_BYTES) {
            throw new ParameterException(
                    ParameterErrorCode.PACKAGE_FILE_INVALID,
                    "src/version.h文件大小不合法"
            );
        }

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        long remainingBytes = versionFileSize;
        while (remainingBytes > 0) {
            int readBytes = tarInputStream.read(
                    buffer,
                    0,
                    (int) Math.min(buffer.length, remainingBytes)
            );
            if (readBytes == -1) {
                throw new ParameterException(
                        ParameterErrorCode.PACKAGE_FILE_INVALID,
                        "src/version.h内容不完整"
                );
            }
            outputStream.write(buffer, 0, readBytes);
            remainingBytes -= readBytes;
        }

        String versionFileContent = new String(
                outputStream.toByteArray(),
                StandardCharsets.UTF_8
        );
        Matcher versionMatcher = REDIS_VERSION_DEFINE_PATTERN.matcher(
                versionFileContent
        );
        if (!versionMatcher.find()) {
            throw new ParameterException(
                    ParameterErrorCode.PACKAGE_FILE_INVALID,
                    "src/version.h缺少REDIS_VERSION定义"
            );
        }

        String internalVersion = versionMatcher.group(1).trim();
        if (versionMatcher.find()) {
            throw new ParameterException(
                    ParameterErrorCode.PACKAGE_FILE_INVALID,
                    "src/version.h包含多个REDIS_VERSION定义"
            );
        }
        return internalVersion;
    }

    /**
     * 将临时文件移动到正式存储位置。
     */
    private void moveToStorage(Path temporaryFile, Path storedFile)
            throws IOException {
        if (Files.exists(storedFile)) {
            throw new BusinessException(
                    BusinessCode.OPERATION_ERROR.getCode(),
                    "安装包存储文件已存在但数据库中没有对应记录"
            );
        }

        try {
            Files.move(
                    temporaryFile,
                    storedFile,
                    StandardCopyOption.ATOMIC_MOVE
            );
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporaryFile, storedFile);
        }
    }

    /**
     * 将安装包记录转换为上传结果。
     */
    private VersionPackageUploadVO buildUploadResult(
            RedisVersionPackage versionPackage,
            boolean reusedExisting) {

        VersionPackageUploadVO result = new VersionPackageUploadVO();

        result.setPackageId(versionPackage.getId());
        result.setPackageName(versionPackage.getPackageName());
        result.setRedisVersion(versionPackage.getRedisVersion());
        result.setSha256(versionPackage.getSha256());
        result.setPackageSize(versionPackage.getPackageSize());
        result.setPackageStatus(versionPackage.getPackageStatus());
        result.setUploadedBy(versionPackage.getUploadedBy());
        result.setCreatedTime(versionPackage.getCreatedTime());
        result.setReusedExisting(reusedExisting);

        return result;
    }

    /**
     * 清理临时文件或数据库保存失败后留下的正式文件。
     */
    private void deleteFileQuietly(Path file) {
        if (file == null) {
            return;
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // 清理失败时保留原始业务异常。
        }
    }
}

