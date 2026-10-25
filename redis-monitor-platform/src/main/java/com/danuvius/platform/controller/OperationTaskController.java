package com.danuvius.platform.controller;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.danuvius.platform.common.core.ResultBean;
import com.danuvius.platform.common.constant.AgentTaskConstants;
import com.danuvius.platform.common.constant.ValidationConstants;
import com.danuvius.platform.common.exception.BusinessCode;
import com.danuvius.platform.common.exception.BusinessException;
import com.danuvius.platform.common.exception.ParameterErrorCode;
import com.danuvius.platform.common.exception.ParameterException;
import com.danuvius.platform.model.dto.ManualTerminateTaskRequestDTO;
import com.danuvius.platform.model.dto.TaskResultReportDTO;
import com.danuvius.platform.model.entity.ExecutorInfo;
import com.danuvius.platform.model.enums.ExecutorOperationTypeEnum;
import com.danuvius.platform.model.enums.OperationTaskStepStatusEnum;
import com.danuvius.platform.service.ExecutorInfoService;
import com.danuvius.platform.service.OperationTaskResultService;
import com.danuvius.platform.service.OperationTaskService;
import com.danuvius.platform.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 运维任务接口。
 */
@RestController
@RequestMapping("/api/task")
@RequiredArgsConstructor
public class OperationTaskController {

    private final ExecutorInfoService executorInfoService;

    private final OperationTaskResultService operationTaskResultService;

    private final OperationTaskService operationTaskService;

    /**
     * 接收执行器上报的任务执行结果。
     *
     * @param authorization 执行器认证信息
     * @param request 任务执行结果
     * @return 上报结果
     */
    @PostMapping("/report")
    public ResultBean<Void> reportTaskResult(
            @RequestHeader(
                    value = AgentTaskConstants.AUTHORIZATION_HEADER,
                    required = false
            ) String authorization,
            @RequestBody(required = false) TaskResultReportDTO request) {

        // 1. 校验并规范化执行器上报参数。
        validateRequest(request);
        normalizeRequest(request);

        // 2. 根据Token查询执行器，并回填平台内部执行器编号。
        String executorId = getExecutorIdByToken(authorization);
        request.setAgentId(executorId);

        // 3. 交给业务层处理任务执行结果。
        operationTaskResultService.reportTaskResult(request);

        return ResultBean.success(null);
    }

    /**
     * 人工结束已经进入结果未知状态的运维任务。
     *
     * @param taskNo 任务编号
     * @param request 人工结束信息
     * @return 操作结果
     */
    @PostMapping("/{taskNo}/manualTerminate")
    public ResultBean<Void> manuallyTerminateTask(
            @PathVariable String taskNo,
            @RequestBody ManualTerminateTaskRequestDTO request) {

        String normalizedTaskNo = validateTaskNo(taskNo);

        validateManualTerminationRequest(request);

        operationTaskService.manuallyTerminateTask(
                normalizedTaskNo,
                request.getTerminationReason().trim(),
                request.getTerminatedBy().trim()
        );

        return ResultBean.success(null);
    }

    /**
     * 校验执行器上报的任务结果参数。
     */
    private void validateRequest(TaskResultReportDTO request) {

        if (request == null) {
            throw new ParameterException(
                    ParameterErrorCode.TASK_PARAM_INVALID,
                    "任务结果报文不能为空"
            );
        }

        String taskNo = StringUtils.trimToNull(request.getTaskNo());
        if (taskNo == null
                || !ValidationConstants.TASK_NO_PATTERN
                .matcher(taskNo).matches()) {
            throw new ParameterException(
                    ParameterErrorCode.TASK_NO_INVALID,
                    "任务编号格式不正确"
            );
        }

        if (request.getStepNo() == null || request.getStepNo() <= 0) {
            throw new ParameterException(
                    ParameterErrorCode.STEP_NO_INVALID,
                    "步骤编号必须大于0"
            );
        }

        validateOperationType(request.getOperationType());
        validateResultStatus(request.getStatus());

        String result = StringUtils.trimToNull(request.getResult());
        if (result == null || !JSON.isValidObject(result)) {
            throw new ParameterException(
                    ParameterErrorCode.TASK_PARAM_INVALID,
                    "任务执行结果必须是有效的JSON对象"
            );
        }
    }

    /**
     * 校验人工结束任务请求。
     */
    private void validateManualTerminationRequest(
            ManualTerminateTaskRequestDTO request) {

        if (request == null) {
            throw new ParameterException(
                    ParameterErrorCode.TASK_PARAM_INVALID,
                    "人工结束任务请求不能为空"
            );
        }

        String terminationReason = StringUtils.trimToNull(
                request.getTerminationReason()
        );
        if (terminationReason == null) {
            throw new ParameterException(
                    ParameterErrorCode.TASK_PARAM_INVALID,
                    "人工结束原因不能为空"
            );
        }
        if (terminationReason.length()
                > ValidationConstants.MAX_OPERATION_REASON_LENGTH) {
            throw new ParameterException(
                    ParameterErrorCode.TASK_PARAM_INVALID,
                    "人工结束原因不能超过"
                            + ValidationConstants.MAX_OPERATION_REASON_LENGTH
                            + "个字符"
            );
        }

        String terminatedBy = StringUtils.trimToNull(
                request.getTerminatedBy()
        );
        if (terminatedBy == null) {
            throw new ParameterException(
                    ParameterErrorCode.TASK_PARAM_INVALID,
                    "人工结束操作人不能为空"
            );
        }
        if (terminatedBy.length()
                > ValidationConstants.MAX_CREATED_BY_LENGTH) {
            throw new ParameterException(
                    ParameterErrorCode.TASK_PARAM_INVALID,
                    "人工结束操作人不能超过"
                            + ValidationConstants.MAX_CREATED_BY_LENGTH
                            + "个字符"
            );
        }
    }

    /**
     * 校验并规范化运维任务编号。
     */
    private String validateTaskNo(String taskNo) {
        String normalizedTaskNo = StringUtils.trimToNull(taskNo);
        if (normalizedTaskNo == null
                || !ValidationConstants.TASK_NO_PATTERN
                .matcher(normalizedTaskNo).matches()) {
            throw new ParameterException(
                    ParameterErrorCode.TASK_NO_INVALID,
                    "任务编号格式不正确"
            );
        }
        return normalizedTaskNo;
    }

    /**
     * 校验执行器操作类型。
     */
    private void validateOperationType(String operationType) {

        String normalizedOperationType = StringUtils.trimToNull(
                operationType
        );
        if (normalizedOperationType == null) {
            throw new ParameterException(
                    ParameterErrorCode.TASK_TYPE_EMPTY,
                    "运维操作类型不能为空"
            );
        }

        try {
            ExecutorOperationTypeEnum.valueOf(normalizedOperationType);
        } catch (IllegalArgumentException e) {
            throw new ParameterException(
                    ParameterErrorCode.TASK_TYPE_UNSUPPORTED,
                    "不支持的运维操作类型：" + normalizedOperationType
            );
        }
    }

    /**
     * 校验任务执行状态。
     */
    private void validateResultStatus(String status) {

        String normalizedStatus = StringUtils.trimToNull(status);
        boolean success = OperationTaskStepStatusEnum.SUCCESS.name()
                .equals(normalizedStatus);
        boolean failed = OperationTaskStepStatusEnum.FAILED.name()
                .equals(normalizedStatus);

        if (!success && !failed) {
            throw new ParameterException(
                    ParameterErrorCode.TASK_PARAM_INVALID,
                    "任务执行状态只允许SUCCESS或FAILED"
            );
        }
    }

    /**
     * 根据Token查询执行器编号。
     */
    private String getExecutorIdByToken(String authorization) {

        String token = extractBearerToken(authorization);
        ExecutorInfo executor = executorInfoService.getOne(
                new LambdaQueryWrapper<ExecutorInfo>().eq(
                        ExecutorInfo::getExecutorToken,
                        token
                ),
                false
        );

        if (executor == null) {
            throw new BusinessException(
                    BusinessCode.NO_AUTH_ERROR.getCode(),
                    "无效的执行器Token"
            );
        }

        return executor.getId();
    }
    /**
     * 从Authorization请求头中提取Bearer Token。
     */
    private String extractBearerToken(String authorization) {

        if (authorization == null
                || !authorization.startsWith(
                AgentTaskConstants.BEARER_PREFIX)) {
            throw new BusinessException(
                    BusinessCode.NO_AUTH_ERROR.getCode(),
                    "执行器认证信息格式不正确"
            );
        }

        String token = StringUtils.trimToNull(authorization.substring(
                AgentTaskConstants.BEARER_PREFIX.length()
        ));

        if (token == null) {
            throw new BusinessException(
                    BusinessCode.NO_AUTH_ERROR.getCode(),
                    "执行器Token不能为空"
            );
        }

        return token;
    }

    /**
     * 去除执行器上报字段的首尾空格。
     */
    private void normalizeRequest(TaskResultReportDTO request) {
        request.setTaskNo(request.getTaskNo().trim());
        request.setOperationType(request.getOperationType().trim());
        request.setStatus(request.getStatus().trim());
        request.setResult(request.getResult().trim());
    }

}





