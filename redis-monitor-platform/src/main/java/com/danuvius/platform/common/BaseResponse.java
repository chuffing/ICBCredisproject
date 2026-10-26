package com.danuvius.platform.common;

import com.danuvius.platform.common.exception.BusinessCode;
import lombok.Getter;

import java.io.Serializable;

@Getter
public class BaseResponse<T> implements Serializable {
    private int code;
    private T data;
    private String message;


    public BaseResponse(int code, T data, String message) {
        this.code = code;
        this.data = data;
        this.message = message;
    }

    public BaseResponse(int code, T data) {
        this(code, data, "");
    }

    public BaseResponse(BusinessCode businessCode) {
        this(businessCode.getCode(), null,businessCode.getMsg());
    }
}
