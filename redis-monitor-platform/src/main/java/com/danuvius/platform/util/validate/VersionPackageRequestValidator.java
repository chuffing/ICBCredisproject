package com.danuvius.platform.util.validate;

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
}
