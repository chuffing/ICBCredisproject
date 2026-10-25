package com.danuvius.platform.util.validate;

import com.danuvius.platform.common.exception.ParameterCode;
import com.danuvius.platform.common.exception.ParameterException;
import com.danuvius.platform.common.exception.validator.NumberValidator;
import com.danuvius.platform.model.dto.VersionUpgradeRequestDTO;

/**
 * Redis版本升级请求参数校验。
 */
public final class VersionUpgradeRequestValidator {

    private VersionUpgradeRequestValidator() {
    }

    /**
     * 校验并规范化版本升级请求。
     */
    public static void validate(VersionUpgradeRequestDTO request) {
        if (request == null) {
            throw new ParameterException(
                    ParameterCode.PARAM_NULL,
                    "版本升级请求不能为空"
            );
        }

        NumberValidator.notNull(request.getPackageId(), "packageId");
        NumberValidator.positive(request.getPackageId(), "packageId");

        request.setOperationReason(
                OperationCommonValidator.validateReason(
                        request.getOperationReason(),
                        "operationReason"
                )
        );
        request.setCreatedBy(
                OperationCommonValidator.validateOperator(
                        request.getCreatedBy(),
                        "createdBy"
                )
        );
    }
}
