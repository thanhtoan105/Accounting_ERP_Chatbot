package com.accounting.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.accounting.dto.LoginRequest;
import com.accounting.entity.User;
import com.accounting.repository.AuditLogRepository;
import com.accounting.repository.ImportAuditEntryRepository;
import com.accounting.repository.UserRepository;
import com.accounting.security.JwtTokenProvider;
import com.accounting.security.PasswordEncoder;
import com.accounting.service.AuditService;
import com.accounting.service.EmailService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

  @Mock
  private UserRepository userRepository;

  @Mock
  private AuditLogRepository auditLogRepository;

  @Mock
  private ImportAuditEntryRepository importAuditEntryRepository;

  @Mock
  private HttpServletRequest httpRequest;

  @Mock
  private EmailService emailService;

  private PasswordEncoder passwordEncoder;
  private JwtTokenProvider jwtTokenProvider;
  private AuditService auditService;
  private AuthServiceImpl authService;

  @BeforeEach
  void setUp() {
    passwordEncoder = new PasswordEncoder();
    jwtTokenProvider = new JwtTokenProvider(
        "test-secret-key-minimum-256-bits-for-security-12345678901234567890123456789012345678901234567890", 30, 7);
    auditService = new AuditServiceImpl(
        auditLogRepository, userRepository, importAuditEntryRepository, new ObjectMapper());
    authService = new AuthServiceImpl(userRepository, passwordEncoder, jwtTokenProvider, auditService, emailService);
  }

  @Test
  void login_shouldReturnAccessTokenOnSuccess() {
    User user = new User();
    user.setId(1L);
    user.setEmail("test@example.com");
    String hashedPassword = passwordEncoder.encode("TestPassword123!");
    user.setPasswordHash(hashedPassword);
    user.setFullName("Test User");
    user.setRole("USER");
    user.setFailedLoginCount(0);

    LoginRequest request = new LoginRequest();
    request.setEmail("test@example.com");
    request.setPassword("TestPassword123!");

    org.mockito.Mockito.when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
    org.mockito.Mockito.when(userRepository.save(org.mockito.ArgumentMatchers.any(User.class))).thenReturn(user);
    org.mockito.Mockito.when(httpRequest.getRemoteAddr()).thenReturn("127.0.0.1");
    org.mockito.Mockito.when(httpRequest.getHeader("User-Agent")).thenReturn("TestAgent");

    var response = authService.login(request, httpRequest);

    assertNotNull(response);
    assertNotNull(response.getAccessToken());
    assertNotNull(response.getUser());
    assertEquals("test@example.com", response.getUser().getEmail());
  }

  @Test
  void login_shouldThrowExceptionForInvalidPassword() {
    User user = new User();
    user.setId(1L);
    user.setEmail("test@example.com");
    user.setPasswordHash(passwordEncoder.encode("TestPassword123!"));
    user.setFailedLoginCount(0);

    LoginRequest request = new LoginRequest();
    request.setEmail("test@example.com");
    request.setPassword("WrongPassword123!");

    org.mockito.Mockito.when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
    org.mockito.Mockito.when(userRepository.save(org.mockito.ArgumentMatchers.any(User.class))).thenReturn(user);
    org.mockito.Mockito.when(httpRequest.getRemoteAddr()).thenReturn("127.0.0.1");
    org.mockito.Mockito.when(httpRequest.getHeader("User-Agent")).thenReturn("TestAgent");

    ResponseStatusException exception = assertThrows(ResponseStatusException.class,
        () -> authService.login(request, httpRequest));

    assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
  }
}
