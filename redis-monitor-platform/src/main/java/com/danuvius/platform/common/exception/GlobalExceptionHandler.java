package com.danuvius.platform.common.exception;

import com.danuvius.platform.common.core.ResultBean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResultBean<?> handleBusinessException(BusinessException e) {
        int code = e.getCode() == null ? ErrorCode.SYSTEM_ERROR.getCode() : e.getCode();
        String msg = e.getMsg() == null || e.getMsg().trim().isEmpty()
                ? e.getMessage() : e.getMsg();
        if (msg == null || msg.trim().isEmpty()) {
            msg = ErrorCode.SYSTEM_ERROR.getMsg();
        }
        return ResultBean.resultBean(null, code, msg);
    }

    @ExceptionHandler(ParameterException.class)
    public ResultBean<?> handleParameterException(ParameterException e) {
        int code = e.getCode() == null ? ErrorCode.PARAM_ERROR.getCode() : e.getCode();
        String msg = e.getMsg() == null || e.getMsg().trim().isEmpty()
                ? e.getMessage() : e.getMsg();
        if (msg == null || msg.trim().isEmpty()) {
            msg = ErrorCode.PARAM_ERROR.getMsg();
        }
        return ResultBean.resultBean(null, code, msg);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResultBean<?> handleValidException(MethodArgumentNotValidException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String msg = fieldError == null ? ErrorCode.PARAM_ERROR.getMsg() : fieldError.getDefaultMessage();
        return ResultBean.resultBean(null, ErrorCode.PARAM_ERROR.getCode(), msg);
    }

    @ExceptionHandler(BindException.class)
    public ResultBean<?> handleBindException(BindException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String msg = fieldError == null ? ErrorCode.PARAM_ERROR.getMsg() : fieldError.getDefaultMessage();
        return ResultBean.resultBean(null, ErrorCode.PARAM_ERROR.getCode(), msg);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResultBean<?> handleConstraintViolationException(ConstraintViolationException e) {
        String msg = ErrorCode.PARAM_ERROR.getMsg();
        for (ConstraintViolation<?> violation : e.getConstraintViolations()) {
            msg = violation.getMessage();
            break;
        }
        return ResultBean.resultBean(null, ErrorCode.PARAM_ERROR.getCode(), msg);
    }

    @ExceptionHandler(Exception.class)
    public ResultBean<?> handleException(Exception e) {
        log.error("系统异常", e);
        return ResultBean.resultBean(null, ErrorCode.SYSTEM_ERROR.getCode(), ErrorCode.SYSTEM_ERROR.getMsg());
    }
}
