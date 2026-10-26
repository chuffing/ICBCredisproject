package com.danuvius.platform.util.validate;

import com.danuvius.platform.common.constant.CommonConstant;
import com.danuvius.platform.common.exception.validator.IdValidator;
import com.danuvius.platform.common.exception.validator.ListValidator;
import com.danuvius.platform.common.exception.validator.StringValidator;
import com.danuvius.platform.model.dto.executor.ExecutorManagementRequest;

import java.util.List;

/**
 * 执行器纳管状态变更参数校验。
 */
public class ExecutorManagementRequestValidator {

    /** 单次最多变更的执行器数量 */
    private static final int MAX_SIZE = 100;

    public static void validate(ExecutorManagementRequest req) {
        StringValidator.isEnumValue(req.getManagementStatus(),
                CommonConstant.MANAGEMENT_STATUS, "managementStatus");

        List<String> executorIds = req.getExecutorIds();
        ListValidator.notEmpty(executorIds, "executorIds");
        ListValidator.maxSize(executorIds, MAX_SIZE, "executorIds");
        ListValidator.noBlankStringElement(executorIds, "executorIds");
        for (String executorId : executorIds) {
            // executor_info.id 由 MyBatis-Plus ASSIGN_ID 生成，为雪花数字串
            IdValidator.snowflake(executorId, "executorIds");
        }
    }
}
