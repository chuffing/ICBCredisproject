package com.danuvius.platform.controller;

import com.danuvius.platform.common.core.ResultBean;
import com.danuvius.platform.common.exception.ErrorCodeInfo;
import com.danuvius.platform.model.dto.VersionUpgradeRequestDTO;
import com.danuvius.platform.model.vo.VersionUpgradeResponseVO;
import com.danuvius.platform.service.VersionUpgradeService;
import com.danuvius.platform.util.validate.OperationCommonValidator;
import com.danuvius.platform.util.validate.VersionUpgradeRequestValidator;
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

        VersionUpgradeRequestValidator.validate(request);

        VersionUpgradeResponseVO response = versionUpgradeService.upgrade(request);

        return ResultBean.restResult(response, ErrorCodeInfo.ACCEPTED);
    }

    /**
     * 查询版本升级任务状态。
     */
    @GetMapping("/{taskNo}")
    public ResultBean<VersionUpgradeResponseVO> getUpgradeStatus(
            @PathVariable String taskNo) {

        String normalizedTaskNo =
                OperationCommonValidator.validateTaskNo(taskNo);

        return ResultBean.restResult(
                versionUpgradeService.getUpgradeStatus(normalizedTaskNo),
                ErrorCodeInfo.OK
        );
    }

}
