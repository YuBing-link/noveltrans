package com.yumu.noveltranslator.enums;

/**
 * 通用错误码和错误信息枚举类
 */
public enum ErrorCodeEnum {

    // 通用错误码
    SUCCESS("200", "操作成功"),
    SYSTEM_ERROR("500", "系统内部错误"),
    PARAMETER_ERROR("400", "参数错误"),
    UNAUTHORIZED("401", "未授权访问"),
    FORBIDDEN("403", "禁止访问"),
    NOT_FOUND("404", "资源不存在"),
    REQUEST_TIMEOUT("408", "请求超时"),
    CONFLICT("409", "资源冲突"),

    // 用户相关错误码
    USER_NOT_FOUND("U001", "用户不存在"),
    USER_PASSWORD_ERROR("U002", "密码错误"),
    USER_ACCOUNT_LOCKED("U003", "账户已被锁定"),
    USER_ACCOUNT_DISABLED("U004", "账户已被禁用"),
    USER_EMAIL_EXISTS("U005", "邮箱已被注册"),
    USER_EMAIL_INVALID("U006", "邮箱格式不正确"),
    USER_PASSWORD_TOO_SHORT("U007", "密码长度不够"),
    USER_VERIFICATION_CODE_ERROR("U008", "验证码错误或已过期"),

    // 翻译相关错误码
    TRANSLATION_ENGINE_UNAVAILABLE("T001", "翻译引擎不可用"),
    TRANSLATION_RATE_LIMIT("T002", "翻译频率限制"),
    TRANSLATION_UNSUPPORTED_LANGUAGE("T003", "不支持的语言"),
    TRANSLATION_EMPTY_CONTENT("T004", "翻译内容为空"),
    TRANSLATION_FAILED("T005", "翻译失败"),

    // 邮件相关错误码
    EMAIL_SEND_FAILED("E001", "邮件发送失败"),
    EMAIL_INVALID("E002", "无效的邮箱地址"),
    EMAIL_CODE_EXPIRED("E003", "验证码已过期"),

    // Token 相关错误码
    TOKEN_INVALID("T101", "令牌无效或已过期"),
    TOKEN_EXPIRED("T102", "令牌已过期"),
    TOKEN_MISSING("T103", "缺少令牌");

    private final String code;
    private final String message;

    ErrorCodeEnum(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    /**
     * 根据错误码获取枚举
     *
     * @param code 错误码
     * @return 枚举值，如果未找到则返回系统错误
     */
    public static ErrorCodeEnum getByCode(String code) {
        for (ErrorCodeEnum errorCode : values()) {
            if (errorCode.getCode().equals(code)) {
                return errorCode;
            }
        }
        return SYSTEM_ERROR;
    }
}