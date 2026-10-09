package com.redisops.platform.versionupgrade.exception;

/**
 * 表示同一个版本号或摘要已经绑定了不一致的版本包。
 */
public class VersionPackageConflictException extends IllegalStateException {

    /** 创建带有用户可读说明的版本包冲突异常。 */
    public VersionPackageConflictException(String message) {
        super(message);
    }
}
