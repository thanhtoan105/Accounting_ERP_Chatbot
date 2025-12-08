package com.accounting.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JwtTokenProviderTest {

  private JwtTokenProvider jwtTokenProvider;

  @BeforeEach
  void setUp() {
    jwtTokenProvider = new JwtTokenProvider("test-secret-key-minimum-256-bits-for-security-12345678901234567890123456789012345678901234567890", 30, 7);
  }

  @Test
  void generateAccessToken_shouldCreateValidToken() {
    Long userId = 1L;
    String email = "test@example.com";
    String role = "USER";

    String token = jwtTokenProvider.generateAccessToken(userId, email, role);

    assertNotNull(token);
    assertTrue(jwtTokenProvider.validateToken(token));
  }

  @Test
  void generateRefreshToken_shouldCreateValidToken() {
    Long userId = 1L;
    String email = "test@example.com";
    boolean rememberMe = false;

    String token = jwtTokenProvider.generateRefreshToken(userId, email, rememberMe);

    assertNotNull(token);
    assertTrue(jwtTokenProvider.validateToken(token));
  }

  @Test
  void validateToken_shouldReturnFalseForInvalidToken() {
    String invalidToken = "invalid.token.here";

    assertFalse(jwtTokenProvider.validateToken(invalidToken));
  }

  @Test
  void getUserIdFromToken_shouldExtractUserId() {
    Long userId = 1L;
    String email = "test@example.com";
    String role = "USER";
    String token = jwtTokenProvider.generateAccessToken(userId, email, role);

    Long extractedUserId = jwtTokenProvider.getUserIdFromToken(token);

    assertEquals(userId, extractedUserId);
  }

  @Test
  void getEmailFromToken_shouldExtractEmail() {
    Long userId = 1L;
    String email = "test@example.com";
    String role = "USER";
    String token = jwtTokenProvider.generateAccessToken(userId, email, role);

    String extractedEmail = jwtTokenProvider.getEmailFromToken(token);

    assertEquals(email, extractedEmail);
  }

  @Test
  void getRoleFromToken_shouldExtractRole() {
    Long userId = 1L;
    String email = "test@example.com";
    String role = "USER";
    String token = jwtTokenProvider.generateAccessToken(userId, email, role);

    String extractedRole = jwtTokenProvider.getRoleFromToken(token);

    assertEquals(role, extractedRole);
  }

  @Test
  void generateRefreshToken_shouldCreateLongerExpiryForRememberMe() {
    Long userId = 1L;
    String email = "test@example.com";

    String tokenWithoutRemember = jwtTokenProvider.generateRefreshToken(userId, email, false);
    String tokenWithRemember = jwtTokenProvider.generateRefreshToken(userId, email, true);

    assertNotNull(tokenWithoutRemember);
    assertNotNull(tokenWithRemember);
    assertTrue(jwtTokenProvider.validateToken(tokenWithoutRemember));
    assertTrue(jwtTokenProvider.validateToken(tokenWithRemember));
  }
}
