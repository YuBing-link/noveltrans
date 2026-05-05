package com.yumu.noveltranslator.exception;

import com.yumu.noveltranslator.enums.ErrorCodeEnum;
import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {
    private final ErrorCodeEnum errorCode;

    public BusinessException(ErrorCodeEnum errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCodeEnum errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
