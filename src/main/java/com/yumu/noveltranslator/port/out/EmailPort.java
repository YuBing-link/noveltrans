package com.yumu.noveltranslator.port.out;

public interface EmailPort {
    void sendVerificationCode(String to, String code);
    void sendPasswordResetCode(String to, String code);
}
