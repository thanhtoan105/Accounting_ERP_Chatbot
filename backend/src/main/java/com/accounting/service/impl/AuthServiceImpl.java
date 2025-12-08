package com.accounting.service.impl;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.accounting.dto.AuthResponse;
import com.accounting.dto.ForgotPasswordRequest;
import com.accounting.dto.LoginRequest;
import com.accounting.dto.ResetPasswordRequest;
import com.accounting.entity.User;
import com.accounting.repository.UserRepository;
import com.accounting.security.CompanyContext;
import com.accounting.security.JwtTokenProvider;
import com.accounting.security.PasswordEncoder;
import com.accounting.service.AuditService;
import com.accounting.service.AuthService;
import com.accounting.service.EmailService;

import jakarta.servlet.http.HttpServletRequest;

@Service
@Transactional
public class AuthServiceImpl implements AuthService {

  private static final int MAX_FAILED_LOGIN_ATTEMPTS = 5;
  private static final long LOCKOUT_DURATION_MINUTES = 5;

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtTokenProvider jwtTokenProvider;
  private final AuditService auditService;
  private final EmailService emailService;

  public AuthServiceImpl(
      UserRepository userRepository,
      PasswordEncoder passwordEncoder,
      JwtTokenProvider jwtTokenProvider,
      AuditService auditService,
      EmailService emailService) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtTokenProvider = jwtTokenProvider;
    this.auditService = auditService;
    this.emailService = emailService;
  }

@Override
  public AuthResponse login(LoginRequest request, HttpServletRequest httpRequest) {
    String email = request.getEmail().toLowerCase();
    Optional<User> userOpt = userRepository.findByEmail(email);

    if (userOpt.isEmpty()) {
      handleFailedLogin(null, email, httpRequest);
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
    }

    User user = userOpt.get();

    // Check if user is deactivated
    if ("INACTIVE".equals(user.getStatus())) {
      throw new ResponseStatusException(
          HttpStatus.UNAUTHORIZED, "Account is deactivated");
    }

    if (isAccountLocked(user)) {
      throw new ResponseStatusException(HttpStatus.LOCKED, "Account is locked. Please try again later.");
    }

    // Set company context from user's companyId BEFORE any save operations
    // This is required because User implements CompanyScopedEntity and CompanyScopeEnforcer
    // checks company context when saving. During login, we may not have X-Company-Id header,
    // but we can use the user's own companyId from the database.
    // CRITICAL: Must set before password validation to handle failed login attempts correctly
    if (user.getCompanyId() != null) {
      CompanyContext.setCompanyId(user.getCompanyId());
    }

    if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
      // CompanyContext is already set, so handleFailedLogin can save user correctly
      handleFailedLogin(user, email, httpRequest);
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
    }

    clearFailedLoginAttempts(user);
    String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getEmail(), user.getRole());

    AuthResponse.UserResponse userResponse =
        new AuthResponse.UserResponse(
            user.getId(), user.getEmail(), user.getFullName(), user.getRole(), user.getCompanyId());

    AuthResponse response = new AuthResponse();
    response.setAccessToken(accessToken);
    response.setUser(userResponse);

    logSuccessfulLogin(user, httpRequest);

    return response;
  }

  @Override
  public AuthResponse refresh(String refreshToken) {
    if (!jwtTokenProvider.validateToken(refreshToken)) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token");
    }

    Long userId = jwtTokenProvider.getUserIdFromToken(refreshToken);
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

    String newAccessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getEmail(), user.getRole());

    AuthResponse.UserResponse userResponse =
        new AuthResponse.UserResponse(
            user.getId(), user.getEmail(), user.getFullName(), user.getRole(), user.getCompanyId());

    AuthResponse response = new AuthResponse();
    response.setAccessToken(newAccessToken);
    response.setUser(userResponse);

    return response;
  }

  @Override
  public void logout(String refreshToken) {
    // Token invalidation can be handled client-side by removing the cookie
    // Server-side token blacklist can be implemented if needed
  }

  @Override
  public void requestPasswordReset(ForgotPasswordRequest request, HttpServletRequest httpRequest) {
    String email = request.getEmail().toLowerCase();
    Optional<User> userOpt = userRepository.findByEmail(email);

    if (userOpt.isEmpty()) {
      // Explicitly signal that the email does not exist (requested behavior)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email not found");
    }

    if (userOpt.isPresent()) {
      User user = userOpt.get();
      // Set company context from user's companyId BEFORE saving any changes
      // Password reset is an unauthenticated flow, so there is no X-Company-Id header.
      // Without setting this, CompanyScopeEnforcer will reject the save operation.
      if (user.getCompanyId() != null) {
        CompanyContext.setCompanyId(user.getCompanyId());
      }
      String resetToken = UUID.randomUUID().toString();
      Instant expiry = Instant.now().plusSeconds(30 * 60);

      user.setResetToken(resetToken);
      user.setResetTokenExpiry(expiry);
      user.setUpdatedAt(Instant.now());
      userRepository.save(user);

      emailService.sendPasswordResetEmail(user.getEmail(), resetToken);
      logPasswordResetRequest(user, httpRequest);
    }

    // Now we only reach here if the email exists and email was queued/sent
  }

  @Override
  public void resetPassword(ResetPasswordRequest request, HttpServletRequest httpRequest) {
    // Find user by reset token with expiry check
    Optional<User> userOpt =
        userRepository.findByResetTokenAndExpiryAfter(request.getToken(), Instant.now());

    if (userOpt.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired reset token");
    }

    User user = userOpt.get();
    
    // Set company context from user's companyId before saving
    // This is required because User implements CompanyScopedEntity and CompanyScopeEnforcer
    // checks company context when saving. During password reset, we may not have X-Company-Id header,
    // so we set it from the user's own companyId.
    CompanyContext.setCompanyId(user.getCompanyId());
    
    // Update password and clear reset token
    user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
    user.setResetToken(null);
    user.setResetTokenExpiry(null);
    user.setUpdatedAt(Instant.now());
    
    // Save the user - this will persist the password change
    User savedUser = userRepository.save(user);

    // Verify the save worked by checking the saved entity
    if (savedUser.getResetToken() != null) {
      throw new ResponseStatusException(
          HttpStatus.INTERNAL_SERVER_ERROR, "Failed to update password. Please try again.");
    }

    logPasswordResetCompleted(savedUser, httpRequest);
  }

  private boolean isAccountLocked(User user) {
    if (user.getLockedUntil() == null) {
      return false;
    }
    return user.getLockedUntil().isAfter(Instant.now());
  }

  private void handleFailedLogin(User user, String email, HttpServletRequest httpRequest) {
    if (user != null) {
      int failedCount = user.getFailedLoginCount() + 1;
      user.setFailedLoginCount(failedCount);

      if (failedCount >= MAX_FAILED_LOGIN_ATTEMPTS) {
        user.setLockedUntil(Instant.now().plusSeconds(LOCKOUT_DURATION_MINUTES * 60));
      }

      user.setUpdatedAt(Instant.now());
      userRepository.save(user);
    }

    // TODO: Log failed login attempt (IP, user agent, email)
    logFailedLoginAttempt(user, email, httpRequest);
  }

  private void clearFailedLoginAttempts(User user) {
    user.setFailedLoginCount(0);
    user.setLockedUntil(null);
    user.setUpdatedAt(Instant.now());
    userRepository.save(user);
  }

  private void logSuccessfulLogin(User user, HttpServletRequest httpRequest) {
    auditService.logLoginSuccess(user, httpRequest);
  }

  private void logFailedLoginAttempt(User user, String email, HttpServletRequest httpRequest) {
    String reason = user != null && isAccountLocked(user) ? "ACCOUNT_LOCKED" : "INVALID_CREDENTIALS";
    auditService.logLoginFailure(user, email, reason, httpRequest);
  }

  private void logPasswordResetRequest(User user, HttpServletRequest httpRequest) {
    auditService.logPasswordResetRequest(user, httpRequest);
  }

  private void logPasswordResetCompleted(User user, HttpServletRequest httpRequest) {
    auditService.logPasswordResetCompleted(user, httpRequest);
  }
}
