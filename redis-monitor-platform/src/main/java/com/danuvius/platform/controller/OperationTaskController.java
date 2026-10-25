package com.danuvius.platform.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.danuvius.platform.common.core.ResultBean;
import com.danuvius.platform.common.constant.AgentTaskConstants;
import com.danuvius.platform.common.exception.BusinessCode;
import com.danuvius.platform.common.exception.BusinessException;
import com.danuvius.platform.model.dto.ManualTerminateTaskRequestDTO;
import com.danuvius.platform.model.dto.TaskResultReportDTO;
import com.danuvius.platform.model.entity.ExecutorInfo;
import com.danuvius.platform.service.ExecutorInfoService;
import com.danuvius.platform.service.OperationTaskResultService;
import com.danuvius.platform.service.OperationTaskService;
import com.danuvius.platform.util.StringUtils;
import com.danuvius.platform.util.validate.OperationCommonValidator;
import com.danuvius.platform.util.validate.OperationTaskRequestValidator;
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
        OperationTaskRequestValidator.validateReport(request);

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

        String normalizedTaskNo =
                OperationCommonValidator.validateTaskNo(taskNo);

        OperationTaskRequestValidator.validateManualTermination(request);

        operationTaskService.manuallyTerminateTask(
                normalizedTaskNo,
                request.getTerminationReason(),
                request.getTerminatedBy()
        );

        return ResultBean.success(null);
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

}





