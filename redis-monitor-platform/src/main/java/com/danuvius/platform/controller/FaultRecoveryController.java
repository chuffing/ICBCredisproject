package com.danuvius.platform.controller;

import cn.hutool.http.HttpStatus;
import com.danuvius.platform.common.core.ResultBean;
import com.danuvius.platform.common.constant.ValidationConstants;
import com.danuvius.platform.common.exception.ParameterErrorCode;
import com.danuvius.platform.common.exception.ParameterException;
import com.danuvius.platform.model.dto.FaultRecoveryRequestDTO;
import com.danuvius.platform.model.vo.FaultRecoveryResponseVO;
import com.danuvius.platform.service.FaultRecoveryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 故障恢复接口。
 */
@RestController
@RequestMapping("/api/faultRecovery")
@RequiredArgsConstructor
public class FaultRecoveryController {

    private final FaultRecoveryService faultRecoveryService;

    /**
     * 执行当前Redis环境的故障恢复。
     *
     * @param request 故障恢复请求
     * @return 故障恢复结果
     */
    @PostMapping
    public ResultBean<FaultRecoveryResponseVO> faultRecovery(
            @RequestBody FaultRecoveryRequestDTO request) {

        // 校验请求参数。
        validateRequest(request);

        FaultRecoveryResponseVO response = faultRecoveryService.faultRecovery(request);

        return new ResultBean<>(
                HttpStatus.HTTP_ACCEPTED,
                "故障恢复任务已接收",
                response
        );
    }

    /**
     * 查询故障恢复任务状态。
     *
     * @param taskNo 故障恢复任务编号
     * @return 故障恢复任务状态
     */
    @GetMapping("/{taskNo}")
    public ResultBean<FaultRecoveryResponseVO> getFaultRecoveryStatus(
            @PathVariable String taskNo) {

        // 校验并规范化任务编号。
        String normalizedTaskNo = validateTaskNo(taskNo);

        FaultRecoveryResponseVO response = faultRecoveryService
                .getFaultRecoveryStatus(normalizedTaskNo);

        return ResultBean.success(response);
    }

    /**
     * 校验故障恢复请求参数。
     */
    private void validateRequest(FaultRecoveryRequestDTO request) {

        if (request == null
                || request.getOperationReason() == null
                || request.getOperationReason().trim().isEmpty()) {
            throw new ParameterException(
                    ParameterErrorCode.TASK_PARAM_INVALID,
                    "操作原因不能为空"
            );
        }

        if (request.getOperationReason().trim().length()
                > ValidationConstants.MAX_OPERATION_REASON_LENGTH) {
            throw new ParameterException(
                    ParameterErrorCode.TASK_PARAM_INVALID,
                    "操作原因不能超过"
                            + ValidationConstants.MAX_OPERATION_REASON_LENGTH
                            + "个字符"
            );
        }

        if (request.getCreatedBy() == null
                || request.getCreatedBy().trim().isEmpty()) {
            throw new ParameterException(
                    ParameterErrorCode.TASK_PARAM_INVALID,
                    "操作人不能为空"
            );
        }

        if (request.getCreatedBy().trim().length()
                > ValidationConstants.MAX_CREATED_BY_LENGTH) {
            throw new ParameterException(
                    ParameterErrorCode.TASK_PARAM_INVALID,
                    "操作人不能超过"
                            + ValidationConstants.MAX_CREATED_BY_LENGTH
                            + "个字符"
            );
        }

        // 设置操作原因。（去首尾字符串）
        request.setOperationReason(request.getOperationReason().trim());

        // 设置操作人。（去首尾字符串）
        request.setCreatedBy(request.getCreatedBy().trim());
    }

    /**
     * 校验并规范化故障恢复任务编号。
     *
     * @param taskNo 故障恢复任务编号
     * @return 去除首尾空格后的任务编号
     */
    private String validateTaskNo(String taskNo) {
        String normalizedTaskNo = taskNo == null ? null : taskNo.trim();
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
}
