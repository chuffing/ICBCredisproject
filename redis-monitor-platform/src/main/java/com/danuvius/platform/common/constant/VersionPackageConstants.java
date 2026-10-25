package com.danuvius.platform.common.constant;

/**
 * Redis版本安装包常量。
 */
public final class VersionPackageConstants {

    /** 工具类不允许实例化。 */
    private VersionPackageConstants() {
    }

    /** 上传后默认保存为可用状态。 */
    public static final String DEFAULT_PACKAGE_STATUS = "ENABLED";

    /** 安装包内允许的最大文件数量。 */
    public static final int MAX_ARCHIVE_ENTRY_COUNT = 50000;

    /** src/version.h允许的最大字节数。 */
    public static final int MAX_VERSION_FILE_BYTES = 4096;

    /** 安装包解压后的最大总字节数，2GB。 */
    public static final long MAX_UNCOMPRESSED_BYTES =
            2L * 1024L * 1024L * 1024L;

    /** SHA-256文件存储后缀。 */
    public static final String PACKAGE_FILE_SUFFIX = ".tar.gz";
}
