package com.danuvius.platform.util.validate;

import com.danuvius.platform.common.constant.ValidationConstants;
import com.danuvius.platform.common.exception.validator.StringValidator;
import org.apache.commons.lang3.StringUtils;

/**
 * 运维接口通用参数校验。
 */
public final class OperationCommonValidator {

    private OperationCommonValidator() {
    }

    /**
     * 校验并规范化运维任务编号。
     */
    public static String validateTaskNo(String taskNo) {
        String normalizedTaskNo = StringUtils.trimToNull(taskNo);
        StringValidator.matches(
                normalizedTaskNo,
                ValidationConstants.TASK_NO_PATTERN,
                "taskNo"
        );
        return normalizedTaskNo;
    }

    /**
     * 校验并规范化操作原因。
     */
    public static String validateReason(
            String reason,
            String fieldName) {
        String normalizedReason = StringUtils.trimToNull(reason);
        StringValidator.notBlank(normalizedReason, fieldName);
        StringValidator.maxLength(
                normalizedReason,
                ValidationConstants.MAX_OPERATION_REASON_LENGTH,
                fieldName
        );
        return normalizedReason;
    }

    /**
     * 校验并规范化操作人。
     */
    public static String validateOperator(
            String operator,
            String fieldName) {
        String normalizedOperator = StringUtils.trimToNull(operator);
        StringValidator.notBlank(normalizedOperator, fieldName);
        StringValidator.maxLength(
                normalizedOperator,
                ValidationConstants.MAX_CREATED_BY_LENGTH,
                fieldName
        );
        return normalizedOperator;
    }
}
