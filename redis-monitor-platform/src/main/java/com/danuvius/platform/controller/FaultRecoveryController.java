package com.danuvius.platform.controller;

import cn.hutool.http.HttpStatus;
import com.danuvius.platform.common.core.ResultBean;
import com.danuvius.platform.model.dto.FaultRecoveryRequestDTO;
import com.danuvius.platform.model.vo.FaultRecoveryResponseVO;
import com.danuvius.platform.service.FaultRecoveryService;
import com.danuvius.platform.util.validate.FaultRecoveryRequestValidator;
import com.danuvius.platform.util.validate.OperationCommonValidator;
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

        // 校验并规范化请求参数。
        FaultRecoveryRequestValidator.validate(request);

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
        String normalizedTaskNo =
                OperationCommonValidator.validateTaskNo(taskNo);

        FaultRecoveryResponseVO response = faultRecoveryService
                .getFaultRecoveryStatus(normalizedTaskNo);

        return ResultBean.success(response);
    }

}
