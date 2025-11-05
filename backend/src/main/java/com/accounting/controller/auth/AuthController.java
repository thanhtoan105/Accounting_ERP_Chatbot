package com.accounting.controller.auth;

import com.accounting.dto.AuthResponse;
import com.accounting.dto.ForgotPasswordRequest;
import com.accounting.dto.LoginRequest;
import com.accounting.dto.ResetPasswordRequest;
import com.accounting.security.JwtTokenProvider;
import com.accounting.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

  private final AuthService authService;
  private final JwtTokenProvider jwtTokenProvider;
  private static final String REFRESH_TOKEN_COOKIE_NAME = "refreshToken";
  private static final int REFRESH_TOKEN_COOKIE_MAX_AGE = 30 * 24 * 60 * 60; // 30 days in seconds

  public AuthController(AuthService authService, JwtTokenProvider jwtTokenProvider) {
    this.authService = authService;
    this.jwtTokenProvider = jwtTokenProvider;
  }

@PostMapping("/login")
  public ResponseEntity<Map<String, Object>> login(
      @Valid @RequestBody LoginRequest request,
      HttpServletRequest httpRequest,
      HttpServletResponse httpResponse) {
    AuthResponse response = authService.login(request, httpRequest);

    String refreshToken = jwtTokenProvider.generateRefreshToken(
        response.getUser().getId(),
        response.getUser().getEmail(),
        request.getRememberMe() != null && request.getRememberMe());
    setRefreshTokenCookie(httpResponse, refreshToken, request.getRememberMe() != null && request.getRememberMe());

    Map<String, Object> body = new HashMap<>();
    body.put("data", response);

    return ResponseEntity.ok(body);
  }

  @PostMapping("/refresh")
  public ResponseEntity<Map<String, Object>> refresh(
      @CookieValue(name = REFRESH_TOKEN_COOKIE_NAME, required = false) String refreshToken,
      HttpServletResponse httpResponse) {
    if (refreshToken == null) {
      Map<String, Object> body = new HashMap<>();
      Map<String, Object> error = new HashMap<>();
      error.put("code", "UNAUTHORIZED");
      error.put("message", "Refresh token not found");
      body.put("error", error);
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
    }

    AuthResponse response = authService.refresh(refreshToken);
    
    String newRefreshToken = jwtTokenProvider.generateRefreshToken(
        response.getUser().getId(),
        response.getUser().getEmail(),
        false);
    setRefreshTokenCookie(httpResponse, newRefreshToken, false);

    Map<String, Object> body = new HashMap<>();
    body.put("data", response);

    return ResponseEntity.ok(body);
  }

  @PostMapping("/logout")
  public ResponseEntity<Void> logout(
      @CookieValue(name = REFRESH_TOKEN_COOKIE_NAME, required = false) String refreshToken,
      HttpServletResponse httpResponse) {
    if (refreshToken != null) {
      authService.logout(refreshToken);
    }

    clearRefreshTokenCookie(httpResponse);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/forgot-password")
  public ResponseEntity<Map<String, Object>> forgotPassword(
      @Valid @RequestBody ForgotPasswordRequest request, HttpServletRequest httpRequest) {
    authService.requestPasswordReset(request, httpRequest);

    Map<String, Object> body = new HashMap<>();
    Map<String, Object> data = new HashMap<>();
    data.put("message", "If an account with that email exists, a reset link has been sent.");
    body.put("data", data);

    return ResponseEntity.ok(body);
  }

  @PostMapping("/reset-password")
  public ResponseEntity<Map<String, Object>> resetPassword(
      @Valid @RequestBody ResetPasswordRequest request, HttpServletRequest httpRequest) {
    authService.resetPassword(request, httpRequest);

    Map<String, Object> body = new HashMap<>();
    Map<String, Object> data = new HashMap<>();
    data.put("message", "Password has been reset successfully");
    body.put("data", data);

    return ResponseEntity.ok(body);
  }

  private void setRefreshTokenCookie(HttpServletResponse response, String refreshToken, boolean rememberMe) {
    ResponseCookie.ResponseCookieBuilder cookieBuilder = ResponseCookie.from(REFRESH_TOKEN_COOKIE_NAME, refreshToken)
        .httpOnly(true)
        .secure(true)
        .path("/")
        .sameSite("Strict");

    if (rememberMe) {
      cookieBuilder.maxAge(REFRESH_TOKEN_COOKIE_MAX_AGE);
    } else {
      cookieBuilder.maxAge(7 * 24 * 60 * 60);
    }

    ResponseCookie cookie = cookieBuilder.build();
    response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
  }

  private void clearRefreshTokenCookie(HttpServletResponse response) {
    ResponseCookie cookie = ResponseCookie.from(REFRESH_TOKEN_COOKIE_NAME, "")
        .httpOnly(true)
        .secure(true)
        .path("/")
        .maxAge(0)
        .sameSite("Strict")
        .build();
    response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
  }
}

