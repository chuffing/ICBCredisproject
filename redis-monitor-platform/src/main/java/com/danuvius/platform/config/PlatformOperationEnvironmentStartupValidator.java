package com.danuvius.platform.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 平台启动时一次性校验运维功能依赖的固定目录。
 */
@Component
public class PlatformOperationEnvironmentStartupValidator {

    private static final String CURRENT_DIRECTORY_NAME = "current";
    private static final String STAGING_DIRECTORY_NAME = "staging";

    private final String versionPackageStorageDir;
    private final String nodeConfigDir;

    public PlatformOperationEnvironmentStartupValidator(
            @Value("${platform.version-package.storage-dir}")
            String versionPackageStorageDir,
            @Value("${platform.node-config.config-dir}")
            String nodeConfigDir) {
        this.versionPackageStorageDir = versionPackageStorageDir;
        this.nodeConfigDir = nodeConfigDir;
    }

    @PostConstruct
    public void validate() {
        // 1. 安装包根目录由运维人员提前创建，平台启动时确认可用。
        requireDirectory(
                versionPackageStorageDir,
                "Redis安装包存储目录"
        );

        // 2. 节点配置根目录以及两个固定子目录都必须提前准备完成。
        Path nodeConfigDirectory = requireDirectory(
                nodeConfigDir,
                "Redis节点配置根目录"
        );
        requireDirectory(
                nodeConfigDirectory.resolve(CURRENT_DIRECTORY_NAME),
                "Redis节点current配置目录"
        );
        requireDirectory(
                nodeConfigDirectory.resolve(STAGING_DIRECTORY_NAME),
                "Redis节点staging配置目录"
        );
    }

    private Path requireDirectory(String configuredPath, String name) {
        Path path = parseCleanAbsolutePath(configuredPath, name);
        requireDirectory(path, name);
        return path;
    }

    private void requireDirectory(Path path, String name) {
        if (!Files.isDirectory(path)) {
            throw new IllegalStateException(name + "不存在或不是目录：" + path);
        }
        if (!Files.isReadable(path)) {
            throw new IllegalStateException(name + "不可读：" + path);
        }
        if (!Files.isWritable(path)) {
            throw new IllegalStateException(name + "不可写：" + path);
        }
        if (!Files.isExecutable(path)) {
            throw new IllegalStateException(name + "不可访问：" + path);
        }
    }

    private Path parseCleanAbsolutePath(String configuredPath, String name) {
        if (configuredPath == null || configuredPath.isEmpty()) {
            throw new IllegalStateException(name + "不能为空");
        }
        if (Character.isWhitespace(configuredPath.charAt(0))
                || Character.isWhitespace(
                        configuredPath.charAt(configuredPath.length() - 1))) {
            throw new IllegalStateException(name + "不能包含首尾空格");
        }

        try {
            Path path = Paths.get(configuredPath);
            if (!path.isAbsolute()) {
                throw new IllegalStateException(name + "必须使用绝对路径");
            }

            // 不修正配置，直接拒绝需要normalize处理的路径段。
            for (Path pathSegment : path) {
                String segment = pathSegment.toString();
                if (".".equals(segment) || "..".equals(segment)) {
                    throw new IllegalStateException(
                            name + "不能包含.或..路径段：" + configuredPath
                    );
                }
            }
            return path;
        } catch (InvalidPathException exception) {
            throw new IllegalStateException(name + "不是合法路径", exception);
        }
    }
}
