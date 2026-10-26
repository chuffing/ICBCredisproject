package com.danuvius.platform.util.validate;

import com.danuvius.platform.common.exception.validator.NumberValidator;

/**
 * Redis版本安装包接口参数校验。
 */
public final class VersionPackageRequestValidator {

    private VersionPackageRequestValidator() {
    }

    /**
     * 校验并规范化安装包上传操作人。
     */
    public static String validateUploadedBy(String uploadedBy) {
        return OperationCommonValidator.validateOperator(
                uploadedBy,
                "uploadedBy"
        );
    }

    /**
     * 校验安装包分页参数。
     */
    public static void validatePage(Integer pageNum, Integer pageSize) {
        NumberValidator.notNull(pageNum, "pageNum");
        NumberValidator.valueBetween(pageNum, 1, 100000, "pageNum");

        NumberValidator.notNull(pageSize, "pageSize");
        NumberValidator.valueBetween(pageSize, 1, 20, "pageSize");
    }

    /**
     * 校验安装包记录编号。
     */
    public static void validatePackageId(Long packageId) {
        NumberValidator.notNull(packageId, "packageId");
        NumberValidator.positive(packageId, "packageId");
    }
}
