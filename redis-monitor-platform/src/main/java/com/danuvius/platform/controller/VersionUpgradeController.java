package com.danuvius.platform.controller;

import cn.hutool.http.HttpStatus;
import com.danuvius.platform.common.constant.ValidationConstants;
import com.danuvius.platform.common.core.ResultBean;
import com.danuvius.platform.common.exception.ParameterErrorCode;
import com.danuvius.platform.common.exception.ParameterException;
import com.danuvius.platform.model.dto.VersionUpgradeRequestDTO;
import com.danuvius.platform.model.vo.VersionUpgradeResponseVO;
import com.danuvius.platform.service.VersionUpgradeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Redis版本升级接口。
 */
@RestController
@RequestMapping("/api/versionUpgrade")
@RequiredArgsConstructor
public class VersionUpgradeController {

    private final VersionUpgradeService versionUpgradeService;

    /**
     * 创建完整的Redis版本升级任务。
     */
    @PostMapping
    public ResultBean<VersionUpgradeResponseVO> upgrade(
            @RequestBody VersionUpgradeRequestDTO request) {

        validateRequest(request);

        VersionUpgradeResponseVO response = versionUpgradeService.upgrade(request);

        return new ResultBean<>(
                HttpStatus.HTTP_ACCEPTED,
                "版本升级任务已接收",
                response
        );
    }

    /**
     * 查询版本升级任务状态。
     */
    @GetMapping("/{taskNo}")
    public ResultBean<VersionUpgradeResponseVO> getUpgradeStatus(
            @PathVariable String taskNo) {

        String normalizedTaskNo = taskNo == null ? null : taskNo.trim();
        if (normalizedTaskNo == null
                || !ValidationConstants.TASK_NO_PATTERN
                .matcher(normalizedTaskNo).matches()) {
            throw new ParameterException(
                    ParameterErrorCode.TASK_NO_INVALID,
                    "任务编号格式不正确"
            );
        }

        return ResultBean.success(
                versionUpgradeService.getUpgradeStatus(normalizedTaskNo)
        );
    }

    private void validateRequest(VersionUpgradeRequestDTO request) {
        if (request == null || request.getPackageId() == null
                || request.getPackageId() <= 0) {
            throw new ParameterException(
                    ParameterErrorCode.TASK_PARAM_INVALID,
                    "安装包编号必须大于0"
            );
        }
        if (request.getOperationReason() == null
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

        request.setOperationReason(request.getOperationReason().trim());
        request.setCreatedBy(request.getCreatedBy().trim());
    }
}
