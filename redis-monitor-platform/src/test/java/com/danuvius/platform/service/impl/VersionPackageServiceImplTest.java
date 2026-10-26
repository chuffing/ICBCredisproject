package com.danuvius.platform.service.impl;

import com.danuvius.platform.common.exception.ParameterException;
import com.danuvius.platform.mapper.RedisVersionPackageMapper;
import com.danuvius.platform.model.entity.RedisVersionPackage;
import com.danuvius.platform.model.vo.VersionPackageUploadVO;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Redis源码包上传的真实压缩包校验测试。 */
class VersionPackageServiceImplTest {

    @TempDir
    Path storageDirectory;

    @Test
    void shouldStoreValidPackageAfterInspectingInternalVersion() throws Exception {
        RedisVersionPackageMapper mapper = mock(RedisVersionPackageMapper.class);
        when(mapper.selectOne(any())).thenReturn(null);
        when(mapper.insert(any())).thenAnswer(invocation -> {
            RedisVersionPackage entity = invocation.getArgument(0);
            entity.setId(7L);
            return 1;
        });
        VersionPackageServiceImpl service = newService(mapper);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "redis-7.2.4.tar.gz",
                "application/gzip",
                createRedisArchive("7.2.4", false)
        );

        VersionPackageUploadVO result = service.upload("tester", file);

        assertEquals(7L, result.getPackageId());
        assertEquals("7.2.4", result.getRedisVersion());
        assertEquals("redis-7.2.4.tar.gz", result.getPackageName());
        assertTrue(Files.isRegularFile(
                storageDirectory.resolve("redis-7.2.4.tar.gz")));
        assertFalse(result.isReusedExisting());
    }

    @Test
    void shouldRejectFileNameAndInternalVersionMismatchAndCleanTemporaryFile()
            throws Exception {
        RedisVersionPackageMapper mapper = mock(RedisVersionPackageMapper.class);
        VersionPackageServiceImpl service = newService(mapper);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "redis-7.2.5.tar.gz",
                "application/gzip",
                createRedisArchive("7.2.4", false)
        );

        assertThrows(ParameterException.class,
                () -> service.upload("tester", file));

        assertEquals(0L, Files.list(storageDirectory).count());
    }

    @Test
    void shouldRejectArchivePathTraversalAndCleanTemporaryFile()
            throws Exception {
        RedisVersionPackageMapper mapper = mock(RedisVersionPackageMapper.class);
        VersionPackageServiceImpl service = newService(mapper);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "redis-7.2.4.tar.gz",
                "application/gzip",
                createRedisArchive("7.2.4", true)
        );

        assertThrows(ParameterException.class,
                () -> service.upload("tester", file));

        assertEquals(0L, Files.list(storageDirectory).count());
    }

    private VersionPackageServiceImpl newService(
            RedisVersionPackageMapper mapper) {
        VersionPackageServiceImpl service =
                new VersionPackageServiceImpl(mapper);
        ReflectionTestUtils.setField(
                service, "storageDir", storageDirectory.toString());
        ReflectionTestUtils.setField(
                service, "maxBytes", 10L * 1024L * 1024L);
        return service;
    }

    private byte[] createRedisArchive(String version, boolean addUnsafeEntry)
            throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GzipCompressorOutputStream gzip =
                     new GzipCompressorOutputStream(bytes);
             TarArchiveOutputStream tar = new TarArchiveOutputStream(gzip)) {
            String root = "redis-" + version;
            addFile(tar, root + "/Makefile", "all:\n\t@true\n");
            addFile(tar, root + "/src/server.c", "int main(void){return 0;}\n");
            addFile(tar, root + "/src/redis-cli.c", "int main(void){return 0;}\n");
            addFile(tar, root + "/src/version.h",
                    "#define REDIS_VERSION \"" + version + "\"\n");
            if (addUnsafeEntry) {
                addFile(tar, root + "/../escape.txt", "unsafe");
            }
            tar.finish();
        }
        return bytes.toByteArray();
    }

    private void addFile(TarArchiveOutputStream tar, String name, String content)
            throws Exception {
        byte[] data = content.getBytes(StandardCharsets.UTF_8);
        TarArchiveEntry entry = new TarArchiveEntry(name);
        entry.setSize(data.length);
        tar.putArchiveEntry(entry);
        tar.write(data);
        tar.closeArchiveEntry();
    }
}
