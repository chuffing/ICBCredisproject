package com.redisops.platform.versionupgrade.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.redisops.platform.versionupgrade.config.VersionPackageProperties;
import com.redisops.platform.versionupgrade.entity.RedisVersionPackage;
import com.redisops.platform.versionupgrade.exception.VersionPackageConflictException;
import com.redisops.platform.versionupgrade.mapper.RedisVersionPackageMapper;
import com.redisops.platform.versionupgrade.model.VersionPackageData;
import com.redisops.platform.versionupgrade.service.VersionPackageService;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 在平台本地以不可变文件方式保存 Redis tar.gz 版本包。
 */
@Service
public class VersionPackageServiceImpl implements VersionPackageService {

    private static final int BUFFER_SIZE = 8192;
    private static final int MAX_ARCHIVE_ENTRIES = 100000;
    private static final long MAX_INSPECTED_BYTES = 2L * 1024 * 1024 * 1024;
    private static final Pattern VERSION_PATTERN =
            Pattern.compile("[0-9]+(?:\\.[0-9]+){1,3}");
    private static final Pattern SHA256_PATTERN = Pattern.compile("[0-9a-f]{64}");

    private final RedisVersionPackageMapper packageMapper;
    private final VersionPackageProperties properties;

    /** 注入版本包数据库访问和存储配置。 */
    public VersionPackageServiceImpl(RedisVersionPackageMapper packageMapper,
                                     VersionPackageProperties properties) {
        this.packageMapper = packageMapper;
        this.properties = properties;
    }

    /**
     * 流式保存上传文件、计算 SHA-256、检查 tar.gz 结构并写入元数据。
     *
     * <p>同版本同摘要直接返回已有记录；同版本不同摘要或同摘要不同版本均拒绝，
     * 保证一个版本号始终绑定唯一内容。</p>
     */
    @Override
    public synchronized VersionPackageData upload(String version, String uploadedBy,
                                                  MultipartFile file) {
        String normalizedVersion = requireVersion(version);
        String normalizedUploader = requireText(uploadedBy, "uploadedBy 不能为空");
        validateUpload(file);

        Path temporaryFile = null;
        boolean movedToFinalPath = false;
        boolean databaseInserted = false;
        Path finalPath = null;
        try {
            Path storageDirectory = getStorageDirectory();
            Files.createDirectories(storageDirectory);
            temporaryFile = Files.createTempFile(storageDirectory, ".upload-", ".part");

            SavedFile saved = saveAndHash(file, temporaryFile);
            RedisVersionPackage duplicate = findByHash(saved.sha256);
            if (duplicate != null) {
                if (!normalizedVersion.equals(duplicate.getDeclaredVersion())) {
                    throw new VersionPackageConflictException(
                            "相同安装包已经登记为版本 " + duplicate.getDeclaredVersion()
                                    + "，不能改用版本 " + normalizedVersion);
                }
                return new VersionPackageData(duplicate, true);
            }

            RedisVersionPackage sameVersion = findByVersion(normalizedVersion);
            if (sameVersion != null) {
                throw new VersionPackageConflictException(
                        "版本 " + normalizedVersion + " 已绑定其他安装包，禁止覆盖");
            }

            inspectArchive(temporaryFile);
            finalPath = storageDirectory.resolve(saved.sha256 + ".tar.gz")
                    .normalize().toAbsolutePath();
            moveWithoutReplacing(temporaryFile, finalPath);
            movedToFinalPath = true;
            temporaryFile = null;

            RedisVersionPackage entity = new RedisVersionPackage();
            entity.setPackageName(safeOriginalFilename(file.getOriginalFilename()));
            entity.setDeclaredVersion(normalizedVersion);
            // Linux 二进制的真实版本由 Agent 部署时执行 redis-server --version 再确认。
            entity.setDetectedVersion(null);
            entity.setStoragePath(finalPath.toString());
            entity.setPackageHash(saved.sha256);
            entity.setPackageSize(saved.size);
            entity.setVerifyStatus("PASSED");
            entity.setVerifyMessage("平台结构和摘要校验通过，实际程序版本将在Agent部署时复核");
            entity.setUploadedBy(normalizedUploader);
            packageMapper.insert(entity);
            databaseInserted = true;
            return new VersionPackageData(entity);
        } catch (VersionPackageConflictException | IllegalArgumentException e) {
            throw e;
        } catch (IOException e) {
            throw new IllegalStateException("版本包保存或校验失败: " + rootMessage(e), e);
        } finally {
            deleteQuietly(temporaryFile);
            // 数据库写入失败时删除本次刚移动的孤立文件；已有文件不会走到这里。
            if (movedToFinalPath && !databaseInserted && finalPath != null) {
                deleteQuietly(finalPath);
            }
        }
    }

    /** 查询全部版本包并按创建时间倒序返回。 */
    @Override
    public List<VersionPackageData> list() {
        List<RedisVersionPackage> entities = packageMapper.selectList(
                new LambdaQueryWrapper<RedisVersionPackage>()
                        .orderByDesc(RedisVersionPackage::getCreateTime));
        List<VersionPackageData> result = new ArrayList<>();
        for (RedisVersionPackage entity : entities) {
            result.add(new VersionPackageData(entity));
        }
        return result;
    }

    /** 查找一条校验通过且文件仍存在的版本包。 */
    @Override
    public RedisVersionPackage requireAvailable(Long packageId) {
        if (packageId == null || packageId <= 0) {
            throw new IllegalArgumentException("versionPackageId 必须大于 0");
        }
        RedisVersionPackage entity = packageMapper.selectById(packageId);
        if (entity == null) {
            throw new IllegalArgumentException("版本包不存在: " + packageId);
        }
        if (!"PASSED".equals(entity.getVerifyStatus())) {
            throw new IllegalStateException("版本包尚未通过校验: " + entity.getVerifyStatus());
        }
        Path file = requireSafeStoredPath(entity.getStoragePath());
        if (!Files.isRegularFile(file)) {
            throw new IllegalStateException("平台版本包文件不存在: " + entity.getPackageName());
        }
        return entity;
    }

    /** 根据版本和摘要定位下发文件，并防止任务参数引用错误版本。 */
    @Override
    public Path requirePackageFile(String version, String packageSha256) {
        String normalizedVersion = requireVersion(version);
        String normalizedSha = normalizeSha256(packageSha256);
        RedisVersionPackage entity = packageMapper.selectOne(
                new LambdaQueryWrapper<RedisVersionPackage>()
                        .eq(RedisVersionPackage::getDeclaredVersion, normalizedVersion)
                        .eq(RedisVersionPackage::getPackageHash, normalizedSha)
                        .eq(RedisVersionPackage::getVerifyStatus, "PASSED"));
        if (entity == null) {
            throw new IllegalStateException("没有找到任务绑定的可用版本包");
        }
        Path file = requireSafeStoredPath(entity.getStoragePath());
        if (!Files.isRegularFile(file)) {
            throw new IllegalStateException("平台版本包文件不存在");
        }
        return file;
    }

    /** 校验上传对象、扩展名和提前可知的文件大小。 */
    private void validateUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("版本包不能为空");
        }
        String filename = safeOriginalFilename(file.getOriginalFilename());
        if (!filename.toLowerCase(Locale.ROOT).endsWith(".tar.gz")) {
            throw new IllegalArgumentException("版本包只允许使用 .tar.gz 格式");
        }
        long maximumBytes = requireMaximumBytes();
        if (file.getSize() > maximumBytes) {
            throw new IllegalArgumentException("版本包超过允许的大小上限");
        }
    }

    /** 流式写入临时文件，同时计算真实大小和 SHA-256。 */
    private SavedFile saveAndHash(MultipartFile file, Path target) throws IOException {
        MessageDigest digest = createSha256Digest();
        long totalBytes = 0;
        long maximumBytes = requireMaximumBytes();
        byte[] buffer = new byte[BUFFER_SIZE];
        try (InputStream input = new BufferedInputStream(file.getInputStream());
             OutputStream output = new BufferedOutputStream(Files.newOutputStream(target))) {
            int bytesRead;
            while ((bytesRead = input.read(buffer)) != -1) {
                totalBytes += bytesRead;
                if (totalBytes > maximumBytes) {
                    throw new IllegalArgumentException("版本包超过允许的大小上限");
                }
                digest.update(buffer, 0, bytesRead);
                output.write(buffer, 0, bytesRead);
            }
        }
        if (totalBytes == 0) {
            throw new IllegalArgumentException("版本包不能为空");
        }
        return new SavedFile(totalBytes, toHex(digest.digest()));
    }

    /**
     * 读取 tar.gz 目录，拒绝链接、特殊文件、越界路径和缺少必要程序的安装包。
     */
    private void inspectArchive(Path packageFile) throws IOException {
        boolean redisServer = false;
        boolean redisCli = false;
        boolean redisSentinel = false;
        int entryCount = 0;
        long inspectedBytes = 0;
        byte[] buffer = new byte[BUFFER_SIZE];

        try (InputStream fileInput = new BufferedInputStream(Files.newInputStream(packageFile));
             GzipCompressorInputStream gzipInput = new GzipCompressorInputStream(fileInput);
             TarArchiveInputStream tarInput = new TarArchiveInputStream(gzipInput)) {
            TarArchiveEntry entry;
            while ((entry = tarInput.getNextTarEntry()) != null) {
                entryCount++;
                if (entryCount > MAX_ARCHIVE_ENTRIES) {
                    throw new IllegalArgumentException("版本包文件数量超过安全上限");
                }
                String normalizedName = validateEntryPath(entry.getName());
                if (entry.isSymbolicLink() || entry.isLink() || entry.isCharacterDevice()
                        || entry.isBlockDevice() || entry.isFIFO()) {
                    throw new IllegalArgumentException("版本包不能包含链接或特殊文件: "
                            + normalizedName);
                }
                if (!tarInput.canReadEntryData(entry)) {
                    throw new IllegalArgumentException("版本包包含无法读取的条目: "
                            + normalizedName);
                }
                if (entry.isFile()) {
                    int bytesRead;
                    while ((bytesRead = tarInput.read(buffer)) != -1) {
                        inspectedBytes += bytesRead;
                        if (inspectedBytes > MAX_INSPECTED_BYTES) {
                            throw new IllegalArgumentException("版本包解压后内容超过安全上限");
                        }
                    }
                    redisServer |= isRequiredProgram(normalizedName, "redis-server");
                    redisCli |= isRequiredProgram(normalizedName, "redis-cli");
                    redisSentinel |= isRequiredProgram(normalizedName, "redis-sentinel");
                }
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (IOException e) {
            throw new IllegalArgumentException("版本包不是有效的 tar.gz 文件或内容已经损坏", e);
        }

        if (!redisServer || !redisCli || !redisSentinel) {
            throw new IllegalArgumentException(
                    "版本包必须包含 bin/redis-server、bin/redis-cli 和 bin/redis-sentinel");
        }
    }

    /** 校验压缩包条目只能使用目标目录内的相对路径。 */
    private String validateEntryPath(String entryName) {
        if (!StringUtils.hasText(entryName)) {
            throw new IllegalArgumentException("版本包包含空路径条目");
        }
        String unixName = entryName.replace('\\', '/');
        Path path = Paths.get(unixName).normalize();
        if (path.isAbsolute() || path.startsWith("..") || unixName.contains(":")) {
            throw new IllegalArgumentException("版本包包含越界路径: " + entryName);
        }
        return path.toString().replace('\\', '/');
    }

    /** 判断归一化条目是否为 bin 目录中的指定程序。 */
    private boolean isRequiredProgram(String entryName, String programName) {
        return entryName.equals("bin/" + programName)
                || entryName.endsWith("/bin/" + programName);
    }

    /** 把临时文件移动成以摘要命名的不可变正式文件。 */
    private void moveWithoutReplacing(Path source, Path target) throws IOException {
        if (Files.exists(target)) {
            throw new VersionPackageConflictException("相同摘要的版本文件已经存在但没有登记");
        }
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target);
        }
    }

    /** 查询指定 SHA-256 是否已经登记。 */
    private RedisVersionPackage findByHash(String sha256) {
        return packageMapper.selectOne(new LambdaQueryWrapper<RedisVersionPackage>()
                .eq(RedisVersionPackage::getPackageHash, sha256));
    }

    /** 查询指定版本号是否已经登记。 */
    private RedisVersionPackage findByVersion(String version) {
        return packageMapper.selectOne(new LambdaQueryWrapper<RedisVersionPackage>()
                .eq(RedisVersionPackage::getDeclaredVersion, version)
                .last("LIMIT 1"));
    }

    /** 获取并校验平台版本包根目录。 */
    private Path getStorageDirectory() {
        String configured = properties.getStorageDir();
        if (!StringUtils.hasText(configured)) {
            throw new IllegalStateException("平台版本包存储目录不能为空");
        }
        return Paths.get(configured).toAbsolutePath().normalize();
    }

    /** 校验数据库中的路径仍位于平台配置的版本包目录内。 */
    private Path requireSafeStoredPath(String storedPath) {
        if (!StringUtils.hasText(storedPath)) {
            throw new IllegalStateException("版本包存储路径为空");
        }
        Path root = getStorageDirectory();
        Path file = Paths.get(storedPath).toAbsolutePath().normalize();
        if (!file.getParent().equals(root)) {
            throw new IllegalStateException("版本包存储路径不属于平台版本目录");
        }
        return file;
    }

    /** 校验版本号只包含数字和点，避免版本号被用于路径拼接。 */
    private String requireVersion(String version) {
        String normalized = requireText(version, "version 不能为空");
        if (!VERSION_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Redis版本号格式不正确");
        }
        return normalized;
    }

    /** 校验并统一 SHA-256 为小写。 */
    private String normalizeSha256(String value) {
        String normalized = requireText(value, "packageSha256 不能为空")
                .toLowerCase(Locale.ROOT);
        if (!SHA256_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("packageSha256 格式不正确");
        }
        return normalized;
    }

    /** 读取非空文本并去掉首尾空格。 */
    private String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    /** 只保留上传文件名本身，拒绝客户端路径影响平台目录。 */
    private String safeOriginalFilename(String originalFilename) {
        if (!StringUtils.hasText(originalFilename)) {
            throw new IllegalArgumentException("版本包文件名不能为空");
        }
        String normalized = originalFilename.replace('\\', '/');
        String filename = normalized.substring(normalized.lastIndexOf('/') + 1).trim();
        if (!StringUtils.hasText(filename) || filename.length() > 255) {
            throw new IllegalArgumentException("版本包文件名不合法");
        }
        return filename;
    }

    /** 获取经过校验的上传大小上限。 */
    private long requireMaximumBytes() {
        if (properties.getMaxBytes() <= 0) {
            throw new IllegalStateException("版本包大小上限配置不正确");
        }
        return properties.getMaxBytes();
    }

    /** 创建 JVM 内置的 SHA-256 摘要计算器。 */
    private MessageDigest createSha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("当前JVM不支持SHA-256", e);
        }
    }

    /** 把摘要字节转换为固定长度的小写十六进制文本。 */
    private String toHex(byte[] values) {
        StringBuilder result = new StringBuilder(values.length * 2);
        for (byte value : values) {
            result.append(String.format("%02x", value & 0xff));
        }
        return result.toString();
    }

    /** 安静删除临时文件或数据库写入失败后的孤立正式文件。 */
    private void deleteQuietly(Path file) {
        if (file == null) {
            return;
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // 原始上传结果优先；残留文件可以由平台运维清理任务处理。
        }
    }

    /** 获取异常链最内层的错误说明。 */
    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return StringUtils.hasText(current.getMessage())
                ? current.getMessage() : current.getClass().getSimpleName();
    }

    /** 保存阶段计算出的文件大小和摘要。 */
    private static class SavedFile {
        private final long size;
        private final String sha256;

        /** 保存不可变的上传结果。 */
        private SavedFile(long size, String sha256) {
            this.size = size;
            this.sha256 = sha256;
        }
    }
}
