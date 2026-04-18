package com.yumu.noveltranslator.enums;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ErrorCodeEnumTest {

    @Test
    void successCode() {
        assertEquals("200", ErrorCodeEnum.SUCCESS.getCode());
        assertEquals("操作成功", ErrorCodeEnum.SUCCESS.getMessage());
    }

    @Test
    void userNotFound() {
        assertNotNull(ErrorCodeEnum.USER_NOT_FOUND.getCode());
        assertNotNull(ErrorCodeEnum.USER_NOT_FOUND.getMessage());
    }

    @Test
    void userPasswordError() {
        assertNotNull(ErrorCodeEnum.USER_PASSWORD_ERROR.getCode());
        assertNotNull(ErrorCodeEnum.USER_PASSWORD_ERROR.getMessage());
    }

    @Test
    void userPasswordTooShort() {
        assertNotNull(ErrorCodeEnum.USER_PASSWORD_TOO_SHORT.getCode());
        assertNotNull(ErrorCodeEnum.USER_PASSWORD_TOO_SHORT.getMessage());
    }

    @Test
    void userEmailInvalid() {
        assertNotNull(ErrorCodeEnum.USER_EMAIL_INVALID.getCode());
        assertNotNull(ErrorCodeEnum.USER_EMAIL_INVALID.getMessage());
    }

    @Test
    void userEmailExists() {
        assertNotNull(ErrorCodeEnum.USER_EMAIL_EXISTS.getCode());
        assertNotNull(ErrorCodeEnum.USER_EMAIL_EXISTS.getMessage());
    }

    @Test
    void userEmailVerificationError() {
        assertNotNull(ErrorCodeEnum.USER_VERIFICATION_CODE_ERROR.getCode());
        assertNotNull(ErrorCodeEnum.USER_VERIFICATION_CODE_ERROR.getMessage());
    }

    @Test
    void emailSendFailed() {
        assertNotNull(ErrorCodeEnum.EMAIL_SEND_FAILED.getCode());
        assertNotNull(ErrorCodeEnum.EMAIL_SEND_FAILED.getMessage());
    }

    @Test
    void tokenInvalid() {
        assertNotNull(ErrorCodeEnum.TOKEN_INVALID.getCode());
        assertNotNull(ErrorCodeEnum.TOKEN_INVALID.getMessage());
    }

    @Test
    void parameterError() {
        assertNotNull(ErrorCodeEnum.PARAMETER_ERROR.getCode());
        assertNotNull(ErrorCodeEnum.PARAMETER_ERROR.getMessage());
    }

    @Test
    void systemError() {
        assertNotNull(ErrorCodeEnum.SYSTEM_ERROR.getCode());
        assertNotNull(ErrorCodeEnum.SYSTEM_ERROR.getMessage());
    }

    @Test
    void unauthorized() {
        assertNotNull(ErrorCodeEnum.UNAUTHORIZED.getCode());
        assertNotNull(ErrorCodeEnum.UNAUTHORIZED.getMessage());
    }
}
