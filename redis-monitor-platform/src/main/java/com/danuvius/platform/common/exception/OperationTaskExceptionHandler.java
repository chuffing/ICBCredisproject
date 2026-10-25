package com.danuvius.platform.common.exception;

import cn.hutool.http.HttpStatus;
import com.danuvius.platform.common.core.ResultBean;
import com.danuvius.platform.controller.FaultRecoveryController;
import com.danuvius.platform.controller.OperationTaskController;
import com.danuvius.platform.model.vo.ErrorResultVO;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 运维任务接口参数异常处理器。
 *
 * 仅处理当前新增的运维任务接口，不修改平台原有全局异常处理逻辑。
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = {
        FaultRecoveryController.class,
        OperationTaskController.class
})
public class OperationTaskExceptionHandler {

    /**
     * 处理参数校验异常。
     */
    @ExceptionHandler(ParameterException.class)
    public ResultBean<ErrorResultVO> handleParameterException(
            ParameterException exception) {

        ErrorResultVO result = new ErrorResultVO(
                exception.getCode(),
                exception.getMsg(),
                exception.getErrorMsg()
        );

        return ResultBean.resultBean(
                result,
                HttpStatus.HTTP_BAD_REQUEST,
                result.getMsg()
        );
    }
}
