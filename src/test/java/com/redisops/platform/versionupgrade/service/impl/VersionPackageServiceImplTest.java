package com.redisops.platform.versionupgrade.service.impl;

import com.redisops.platform.versionupgrade.config.VersionPackageProperties;
import com.redisops.platform.versionupgrade.entity.RedisVersionPackage;
import com.redisops.platform.versionupgrade.mapper.RedisVersionPackageMapper;
import com.redisops.platform.versionupgrade.model.VersionPackageData;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 验证平台版本包的结构检查和元数据保存。 */
class VersionPackageServiceImplTest {

    @TempDir
    Path temporaryDirectory;

    /** 包含三个必要程序的tar.gz应通过平台结构校验并保存元数据。 */
    @Test
    void shouldAcceptValidArchive() throws Exception {
        RedisVersionPackageMapper mapper = emptyMapper();
        doAnswer(invocation -> {
            RedisVersionPackage entity = invocation.getArgument(0);
            entity.setId(11L);
            return 1;
        }).when(mapper).insert(any(RedisVersionPackage.class));
        VersionPackageServiceImpl service = service(mapper);

        MockMultipartFile file = new MockMultipartFile(
                "file", "redis-7.2.5.tar.gz", "application/gzip",
                archive(true));
        VersionPackageData result = service.upload("7.2.5", "admin", file);

        assertEquals(11L, result.getId());
        assertEquals("7.2.5", result.getVersion());
        assertEquals("PASSED", result.getVerifyStatus());
    }

    /** 缺少redis-sentinel的安装包必须在写入数据库前被拒绝。 */
    @Test
    void shouldRejectArchiveMissingRequiredProgram() throws Exception {
        VersionPackageServiceImpl service = service(emptyMapper());
        MockMultipartFile file = new MockMultipartFile(
                "file", "redis-7.2.5.tar.gz", "application/gzip",
                archive(false));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.upload("7.2.5", "admin", file));
        assertEquals("版本包必须包含 bin/redis-server、bin/redis-cli 和 bin/redis-sentinel",
                error.getMessage());
    }

    /** 完全相同的版本和摘要应复用已有记录，不再保存第二份文件。 */
    @Test
    void shouldReuseIdenticalPackage() throws Exception {
        byte[] archive = archive(true);
        String sha256 = sha256(archive);
        RedisVersionPackage existing = new RedisVersionPackage();
        existing.setId(9L);
        existing.setDeclaredVersion("7.2.5");
        existing.setPackageHash(sha256);
        existing.setVerifyStatus("PASSED");
        existing.setPackageName("redis-7.2.5.tar.gz");

        RedisVersionPackageMapper mapper = mock(RedisVersionPackageMapper.class);
        when(mapper.selectOne(any())).thenReturn(existing);
        VersionPackageServiceImpl service = service(mapper);
        MockMultipartFile file = new MockMultipartFile(
                "file", "renamed-redis.tar.gz", "application/gzip", archive);

        VersionPackageData result = service.upload("7.2.5", "admin", file);
        assertEquals(9L, result.getId());
        assertTrue(result.isReusedExisting());
    }

    /** 创建没有重复版本和摘要记录的Mapper。 */
    private RedisVersionPackageMapper emptyMapper() {
        RedisVersionPackageMapper mapper = mock(RedisVersionPackageMapper.class);
        when(mapper.selectOne(any())).thenReturn(null);
        return mapper;
    }

    /** 创建指向测试临时目录的版本包服务。 */
    private VersionPackageServiceImpl service(RedisVersionPackageMapper mapper) {
        VersionPackageProperties properties = new VersionPackageProperties();
        properties.setStorageDir(temporaryDirectory.toString());
        properties.setMaxBytes(10 * 1024 * 1024);
        return new VersionPackageServiceImpl(mapper, properties);
    }

    /** 创建包含必要Redis程序的最小tar.gz测试文件。 */
    private byte[] archive(boolean includeSentinel) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GzipCompressorOutputStream gzip = new GzipCompressorOutputStream(bytes);
             TarArchiveOutputStream tar = new TarArchiveOutputStream(gzip)) {
            addEntry(tar, "redis/bin/redis-server");
            addEntry(tar, "redis/bin/redis-cli");
            if (includeSentinel) {
                addEntry(tar, "redis/bin/redis-sentinel");
            }
            tar.finish();
        }
        return bytes.toByteArray();
    }

    /** 向测试tar文件写入一个普通文件条目。 */
    private void addEntry(TarArchiveOutputStream tar, String name) throws Exception {
        byte[] content = "test".getBytes(StandardCharsets.UTF_8);
        TarArchiveEntry entry = new TarArchiveEntry(name);
        entry.setSize(content.length);
        tar.putArchiveEntry(entry);
        tar.write(content);
        tar.closeArchiveEntry();
    }

    /** 计算测试字节的SHA-256。 */
    private String sha256(byte[] values) throws Exception {
        byte[] digest = java.security.MessageDigest.getInstance("SHA-256").digest(values);
        StringBuilder result = new StringBuilder();
        for (byte value : digest) {
            result.append(String.format("%02x", value & 0xff));
        }
        return result.toString();
    }
}
