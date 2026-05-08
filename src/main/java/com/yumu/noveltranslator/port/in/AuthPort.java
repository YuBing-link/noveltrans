package com.yumu.noveltranslator.port.in;

import com.yumu.noveltranslator.dto.common.Result;
import com.yumu.noveltranslator.dto.auth.LoginRequest;
import com.yumu.noveltranslator.dto.auth.RegisterRequest;
import com.yumu.noveltranslator.dto.auth.RefreshTokenRequest;
import com.yumu.noveltranslator.dto.auth.ChangePasswordRequest;
import com.yumu.noveltranslator.dto.auth.ResetPasswordRequest;
import com.yumu.noveltranslator.domain.model.User;

import java.util.Optional;

public interface AuthPort {
    Result<User> login(LoginRequest req);
    Result sendVerificationCode(String email);
    Result sendResetCode(String email);
    Result<User> register(RegisterRequest req);
    Result refreshToken(RefreshTokenRequest request);
    Result changePassword(Long userId, ChangePasswordRequest request);
    Result resetPassword(ResetPasswordRequest request);
    Result logout(Long userId, String refreshToken, String jwt);

    Optional<User> getUserById(Long userId);
}
