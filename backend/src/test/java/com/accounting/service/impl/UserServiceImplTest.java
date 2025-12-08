package com.accounting.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import com.accounting.dto.ChangePasswordRequest;
import com.accounting.dto.CreateUserRequest;
import com.accounting.dto.UpdateUserRequest;
import com.accounting.entity.User;
import com.accounting.repository.UserRepository;
import com.accounting.security.CompanyContext;
import com.accounting.security.PasswordEncoder;
import com.accounting.service.AuditService;
import com.accounting.service.EmailService;
import com.accounting.service.RoleService;

import jakarta.servlet.http.HttpServletRequest;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class UserServiceImplTest {

  @Mock private UserRepository userRepository;

  @Mock private AuditService auditService;

  @Mock private EmailService emailService;

  @Mock private HttpServletRequest httpRequest;

  private PasswordEncoder passwordEncoder;
  private RoleService roleService;
  private UserServiceImpl userService;

  @BeforeEach
  void setUp() {
    passwordEncoder = new PasswordEncoder();
    roleService = new RoleServiceImpl();
    userService = new UserServiceImpl(userRepository, passwordEncoder, roleService, auditService, emailService);
    CompanyContext.setCompanyId(1L);
    
    // Set up SecurityContext for getCurrentUserIdFromContext()
    SecurityContext securityContext = org.mockito.Mockito.mock(SecurityContext.class);
    Authentication authentication = org.mockito.Mockito.mock(Authentication.class);
    org.mockito.Mockito.when(securityContext.getAuthentication()).thenReturn(authentication);
    org.mockito.Mockito.when(authentication.getPrincipal()).thenReturn("1"); // User ID as string
    SecurityContextHolder.setContext(securityContext);
  }

  @AfterEach
  void tearDown() {
    CompanyContext.clear();
    SecurityContextHolder.clearContext();
  }

  @Test
  void updateUserRole_shouldPreventSelfRoleChange() {
    Long currentUserId = 1L;
    Long targetUserId = 1L; // Same user

    // No need to mock repository since validation happens before repository access
    ResponseStatusException exception =
        assertThrows(
            ResponseStatusException.class,
            () ->
                userService.updateUserRole(
                    targetUserId, "admin", currentUserId, "admin", httpRequest));

    assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
    assertEquals("You cannot change your own role", exception.getReason());
  }

  @Test
  void updateUserRole_shouldRejectUnauthorizedUser() {
    Long currentUserId = 2L;
    Long targetUserId = 3L;

    ResponseStatusException exception =
        assertThrows(
            ResponseStatusException.class,
            () ->
                userService.updateUserRole(
                    targetUserId, "admin", currentUserId, "accountant", httpRequest));

    assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
    assertEquals("Only ADMIN or CHIEF_ACCOUNTANT can change user roles", exception.getReason());
  }

  @Test
  void updateUserRole_shouldRejectInvalidRole() {
    Long currentUserId = 1L;
    Long targetUserId = 2L;

    // Invalid role validation happens before repository access, so no need to mock
    ResponseStatusException exception =
        assertThrows(
            ResponseStatusException.class,
            () ->
                userService.updateUserRole(
                    targetUserId, "invalid_role", currentUserId, "admin", httpRequest));

    assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
  }

  @Test
  void createUser_shouldAssignDefaultRoleWhenNotSpecified() {
    CreateUserRequest request = new CreateUserRequest();
    request.setEmail("newuser@example.com");
    request.setPassword("TestPassword123!");
    request.setFullName("New User");
    request.setRole(null); // Not specified

    // Mock current user to get through permission checks
    User currentUser = new User();
    currentUser.setId(1L);
    currentUser.setRole("admin");
    
    org.mockito.Mockito.when(userRepository.existsByEmail(request.getEmail())).thenReturn(false);
    org.mockito.Mockito.when(userRepository.findById(1L)).thenReturn(Optional.of(currentUser));
    org.mockito.Mockito.when(userRepository.save(org.mockito.ArgumentMatchers.any(User.class)))
        .thenAnswer(invocation -> {
          User user = invocation.getArgument(0);
          user.setId(1L);
          return user;
        });
    // Use lenient stubbing for HTTP request mocks since they may not always be called
    org.mockito.Mockito.lenient().when(httpRequest.getRemoteAddr()).thenReturn("127.0.0.1");
    org.mockito.Mockito.lenient().when(httpRequest.getHeader("User-Agent")).thenReturn("test-agent");
    // Mock audit service to avoid errors
    org.mockito.Mockito.doNothing().when(auditService).logUserCreated(
        org.mockito.ArgumentMatchers.any(User.class),
        org.mockito.ArgumentMatchers.any(),
        org.mockito.ArgumentMatchers.any());

    // Execute the method - it should assign default role
    User createdUser = userService.createUser(request, httpRequest);

    // Verify default role was assigned
    assertEquals("accountant", createdUser.getRole());
    assertEquals("ACTIVE", createdUser.getStatus());
  }

  @Test
  void createUser_shouldRejectInvalidRole() {
    CreateUserRequest request = new CreateUserRequest();
    request.setEmail("newuser@example.com");
    request.setPassword("TestPassword123!");
    request.setFullName("New User");
    request.setRole("invalid_role");

    org.mockito.Mockito.when(userRepository.existsByEmail(request.getEmail())).thenReturn(false);

    ResponseStatusException exception =
        assertThrows(
            ResponseStatusException.class, () -> userService.createUser(request, httpRequest));

    assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
  }

  @Test
  void updateUser_shouldPreventSelfRoleChange() {
    Long currentUserId = 1L;
    Long targetUserId = 1L;

    User targetUser = new User();
    targetUser.setId(targetUserId);
    targetUser.setFullName("Test User");
    targetUser.setRole("accountant");
    targetUser.setStatus("ACTIVE");

    UpdateUserRequest request = new UpdateUserRequest();
    request.setFullName("Updated Name");
    request.setRole("admin");

    org.mockito.Mockito.when(userRepository.findById(targetUserId)).thenReturn(Optional.of(targetUser));

    ResponseStatusException exception =
        assertThrows(
            ResponseStatusException.class,
            () -> userService.updateUser(targetUserId, request, currentUserId, "admin", httpRequest));

    assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
    assertEquals("You cannot change your own role", exception.getReason());
  }

  @Test
  void updateUser_shouldPreventSelfStatusChange() {
    Long currentUserId = 1L;
    Long targetUserId = 1L;

    User targetUser = new User();
    targetUser.setId(targetUserId);
    targetUser.setFullName("Test User");
    targetUser.setStatus("ACTIVE");

    UpdateUserRequest request = new UpdateUserRequest();
    request.setStatus("INACTIVE");

    org.mockito.Mockito.when(userRepository.findById(targetUserId)).thenReturn(Optional.of(targetUser));

    ResponseStatusException exception =
        assertThrows(
            ResponseStatusException.class,
            () -> userService.updateUser(targetUserId, request, currentUserId, "admin", httpRequest));

    assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
    assertEquals("You cannot change your own status", exception.getReason());
  }

  @Test
  void deactivateUser_shouldPreventSelfDeactivation() {
    Long currentUserId = 1L;
    Long targetUserId = 1L;
    String currentUserRole = "ADMIN";

    // Self-deactivation check happens before repository call, but we still need to handle the case
    // Since validation is early, exception should be thrown before repository access
    ResponseStatusException exception =
        assertThrows(
            ResponseStatusException.class,
            () -> userService.deactivateUser(targetUserId, currentUserId, currentUserRole, httpRequest));

    assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
    assertEquals("You cannot deactivate your own account", exception.getReason());
  }

  @Test
  void changePassword_shouldRejectIncorrectCurrentPassword() {
    Long userId = 1L;

    User user = new User();
    user.setId(userId);
    user.setPasswordHash(passwordEncoder.encode("CorrectPassword123!"));

    ChangePasswordRequest request = new ChangePasswordRequest();
    request.setCurrentPassword("WrongPassword123!");
    request.setNewPassword("NewPassword123!");
    request.setConfirmPassword("NewPassword123!");

    org.mockito.Mockito.when(userRepository.findById(userId)).thenReturn(Optional.of(user));

    ResponseStatusException exception =
        assertThrows(
            ResponseStatusException.class,
            () -> userService.changePassword(userId, request, httpRequest));

    assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    assertEquals("Current password is incorrect", exception.getReason());
  }

  @Test
  void changePassword_shouldRejectPasswordMismatch() {
    Long userId = 1L;

    User user = new User();
    user.setId(userId);
    user.setPasswordHash(passwordEncoder.encode("CurrentPassword123!"));

    ChangePasswordRequest request = new ChangePasswordRequest();
    request.setCurrentPassword("CurrentPassword123!");
    request.setNewPassword("NewPassword123!");
    request.setConfirmPassword("DifferentPassword123!");

    org.mockito.Mockito.when(userRepository.findById(userId)).thenReturn(Optional.of(user));

    ResponseStatusException exception =
        assertThrows(
            ResponseStatusException.class,
            () -> userService.changePassword(userId, request, httpRequest));

    assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    assertEquals("New password and confirm password do not match", exception.getReason());
  }
}
