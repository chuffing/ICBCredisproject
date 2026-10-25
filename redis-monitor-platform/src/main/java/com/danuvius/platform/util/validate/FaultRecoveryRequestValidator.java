package com.danuvius.platform.util.validate;

import com.danuvius.platform.common.exception.ParameterCode;
import com.danuvius.platform.common.exception.ParameterException;
import com.danuvius.platform.model.dto.FaultRecoveryRequestDTO;

/**
 * 故障恢复请求参数校验。
 */
public final class FaultRecoveryRequestValidator {

    private FaultRecoveryRequestValidator() {
    }

    /**
     * 校验并规范化故障恢复请求。
     */
    public static void validate(FaultRecoveryRequestDTO request) {
        if (request == null) {
            throw new ParameterException(
                    ParameterCode.PARAM_NULL,
                    "故障恢复请求不能为空"
            );
        }

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
