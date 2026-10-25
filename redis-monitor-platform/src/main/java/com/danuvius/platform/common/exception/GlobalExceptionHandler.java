package com.danuvius.platform.common.exception;

import com.danuvius.platform.common.core.ResultBean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResultBean<Void> handleBusinessException(
            BusinessException exception) {
        return ResultBean.error(
                exception.getCode(),
                exception.getMessage()
        );
    }

    @ExceptionHandler(ParameterException.class)
    public ResultBean<Void> handleParameterException(
            ParameterException exception) {
        return ResultBean.error(
                exception.getCode(),
                exception.getErrorMsg()
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResultBean<Void> handleValidException(
            MethodArgumentNotValidException exception) {
        FieldError fieldError = exception.getBindingResult().getFieldError();
        String msg = fieldError == null
                ? ParameterCode.PARAM_ERROR.getMsg()
                : fieldError.getDefaultMessage();
        return ResultBean.error(ParameterCode.PARAM_ERROR.getCode(), msg);
    }

    @ExceptionHandler(BindException.class)
    public ResultBean<Void> handleBindException(
            BindException exception) {
        FieldError fieldError = exception.getBindingResult().getFieldError();
        String msg = fieldError == null
                ? ParameterCode.PARAM_ERROR.getMsg()
                : fieldError.getDefaultMessage();
        return ResultBean.error(ParameterCode.PARAM_ERROR.getCode(), msg);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResultBean<Void> handleConstraintViolationException(
            ConstraintViolationException exception) {
        String msg = ParameterCode.PARAM_ERROR.getMsg();
        for (ConstraintViolation<?> violation : exception.getConstraintViolations()) {
            msg = violation.getMessage();
            break;
        }
        return ResultBean.error(ParameterCode.PARAM_ERROR.getCode(), msg);
    }

    /**
     * 处理请求体缺失或JSON格式错误。
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResultBean<Void> handleMessageNotReadableException() {
        return ResultBean.error(
                ParameterCode.PARAM_FORMAT_ERROR.getCode(),
                "请求体不能为空且必须是有效的JSON"
        );
    }

    /**
     * 处理必填请求参数缺失。
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResultBean<Void> handleMissingRequestParameterException(
            MissingServletRequestParameterException exception) {
        return ResultBean.error(
                ParameterCode.PARAM_NULL.getCode(),
                "字段[" + exception.getParameterName() + "]不能为空"
        );
    }

    /**
     * 处理请求参数类型错误。
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResultBean<Void> handleArgumentTypeMismatchException(
            MethodArgumentTypeMismatchException exception) {
        return ResultBean.error(
                ParameterCode.PARAM_TYPE_ERROR.getCode(),
                "字段[" + exception.getName() + "]类型不正确"
        );
    }

    /**
     * 处理必填上传文件缺失。
     */
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResultBean<Void> handleMissingRequestPartException(
            MissingServletRequestPartException exception) {
        return ResultBean.error(
                ParameterCode.PARAM_FILE_EMPTY.getCode(),
                "字段[" + exception.getRequestPartName() + "]不能为空"
        );
    }

    @ExceptionHandler(Exception.class)
    public ResultBean<Void> handleException(Exception exception) {
        log.error("系统异常", exception);
        return ResultBean.error(
                BusinessCode.SYSTEM_ERROR.getCode(),
                BusinessCode.SYSTEM_ERROR.getMsg()
        );
    }
}
