package com.danuvius.platform.common.exception;

import cn.hutool.http.HttpStatus;
import com.danuvius.platform.common.core.ResultBean;
import com.danuvius.platform.controller.VersionPackageController;
import com.danuvius.platform.model.vo.ErrorResultVO;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Redis版本安装包接口异常处理器。
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = VersionPackageController.class)
public class VersionPackageExceptionHandler {

    /**
     * 返回统一的参数错误结构。
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

    /**
     * 保持安装包接口的响应对象类型一致。
     */
    @ExceptionHandler(BusinessException.class)
    public ResultBean<Void> handleBusinessException(
            BusinessException exception) {
        return ResultBean.error(exception.getCode(), exception.getMessage());
    }
}
