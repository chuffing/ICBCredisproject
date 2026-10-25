package com.danuvius.platform.util.validate;

import com.alibaba.fastjson.JSON;
import com.danuvius.platform.common.exception.ParameterCode;
import com.danuvius.platform.common.exception.ParameterException;
import com.danuvius.platform.common.exception.validator.NumberValidator;
import com.danuvius.platform.common.exception.validator.StringValidator;
import com.danuvius.platform.model.dto.ManualTerminateTaskRequestDTO;
import com.danuvius.platform.model.dto.TaskResultReportDTO;
import com.danuvius.platform.model.enums.ExecutorOperationTypeEnum;
import com.danuvius.platform.model.enums.OperationTaskStepStatusEnum;
import org.apache.commons.lang3.StringUtils;

/**
 * 运维任务请求参数校验。
 */
public final class OperationTaskRequestValidator {

    private OperationTaskRequestValidator() {
    }

    /**
     * 校验并规范化执行器上报的任务结果。
     */
    public static void validateReport(TaskResultReportDTO request) {
        if (request == null) {
            throw new ParameterException(
                    ParameterCode.PARAM_NULL,
                    "任务结果报文不能为空"
            );
        }

        String taskNo = OperationCommonValidator.validateTaskNo(
                request.getTaskNo()
        );

        NumberValidator.notNull(request.getStepNo(), "stepNo");
        NumberValidator.positive(request.getStepNo(), "stepNo");

        String operationType = StringUtils.trimToNull(
                request.getOperationType()
        );
        validateOperationType(operationType);

        String status = StringUtils.trimToNull(request.getStatus());
        validateResultStatus(status);

        String result = StringUtils.trimToNull(request.getResult());
        StringValidator.notBlank(result, "result");
        if (!JSON.isValidObject(result)) {
            throw new ParameterException(
                    ParameterCode.PARAM_FORMAT_ERROR,
                    "字段[result]必须是有效的JSON对象"
            );
        }

        request.setTaskNo(taskNo);
        request.setOperationType(operationType);
        request.setStatus(status);
        request.setResult(result);
    }

    /**
     * 校验并规范化人工结束任务请求。
     */
    public static void validateManualTermination(
            ManualTerminateTaskRequestDTO request) {
        if (request == null) {
            throw new ParameterException(
                    ParameterCode.PARAM_NULL,
                    "人工结束任务请求不能为空"
            );
        }

        request.setTerminationReason(
                OperationCommonValidator.validateReason(
                        request.getTerminationReason(),
                        "terminationReason"
                )
        );
        request.setTerminatedBy(
                OperationCommonValidator.validateOperator(
                        request.getTerminatedBy(),
                        "terminatedBy"
                )
        );
    }

    /**
     * 校验执行器操作类型。
     */
    private static void validateOperationType(String operationType) {
        StringValidator.notBlank(operationType, "operationType");
        try {
            ExecutorOperationTypeEnum.valueOf(operationType);
        } catch (IllegalArgumentException exception) {
            throw new ParameterException(
                    ParameterCode.PARAM_ENUM_ERROR,
                    "字段[operationType]不在允许范围内"
            );
        }
    }

    /**
     * 校验任务执行状态。
     */
    private static void validateResultStatus(String status) {
        StringValidator.notBlank(status, "status");
        boolean success = OperationTaskStepStatusEnum.SUCCESS.name()
                .equals(status);
        boolean failed = OperationTaskStepStatusEnum.FAILED.name()
                .equals(status);
        if (!success && !failed) {
            throw new ParameterException(
                    ParameterCode.PARAM_ENUM_ERROR,
                    "字段[status]只允许SUCCESS或FAILED"
            );
        }
    }
}
