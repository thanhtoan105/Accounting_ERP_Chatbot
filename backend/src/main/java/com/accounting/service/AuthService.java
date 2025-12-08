package com.accounting.service;

import com.accounting.dto.AuthResponse;
import com.accounting.dto.ForgotPasswordRequest;
import com.accounting.dto.LoginRequest;
import com.accounting.dto.ResetPasswordRequest;

import jakarta.servlet.http.HttpServletRequest;

public interface AuthService {
  AuthResponse login(LoginRequest request, HttpServletRequest httpRequest);

  AuthResponse refresh(String refreshToken);

  void logout(String refreshToken);

  void requestPasswordReset(ForgotPasswordRequest request, HttpServletRequest httpRequest);

  void resetPassword(ResetPasswordRequest request, HttpServletRequest httpRequest);
}
