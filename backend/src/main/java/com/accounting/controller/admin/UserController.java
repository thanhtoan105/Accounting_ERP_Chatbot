package com.accounting.controller.admin;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.accounting.dto.AuthResponse;
import com.accounting.dto.ChangePasswordRequest;
import com.accounting.dto.CreateUserRequest;
import com.accounting.dto.RoleUpdateRequest;
import com.accounting.dto.UpdateProfileRequest;
import com.accounting.dto.UpdateUserRequest;
import com.accounting.dto.UserDTO;
import com.accounting.entity.User;
import com.accounting.security.JwtTokenProvider;
import com.accounting.service.UserService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/**
 * REST controller for user management operations.
 * Requires ADMIN or CHIEF_ACCOUNTANT role for most operations.
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

  private final UserService userService;
  private final JwtTokenProvider jwtTokenProvider;

  public UserController(UserService userService, JwtTokenProvider jwtTokenProvider) {
    this.userService = userService;
    this.jwtTokenProvider = jwtTokenProvider;
  }

  /**
   * Get all users in the current company with optional filtering.
   * Requires ADMIN or CHIEF_ACCOUNTANT role.
   *
   * @param role filter by role (optional)
   * @param status filter by status (optional: ACTIVE, INACTIVE, LOCKED)
   * @param search search term for email or full name (optional)
   * @param page page number (default: 0)
   * @param size page size (default: 20)
   * @return paginated list of users
   */
  @GetMapping
  @PreAuthorize("hasRole('ADMIN') or hasRole('CHIEF_ACCOUNTANT')")
  public ResponseEntity<Map<String, Object>> getAllUsers(
      @RequestParam(required = false) String role,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String search,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    Pageable pageable = PageRequest.of(page, size);
    Page<User> userPage = userService.findAllWithFilters(role, status, search, pageable);

    // Convert to UserDTO
    List<UserDTO> userDTOs =
        userPage.getContent().stream()
            .map(
                user ->
                    new UserDTO(
                        user.getId(),
                        user.getEmail(),
                        user.getFullName(),
                        user.getRole(),
                        user.getStatus(),
                        user.getCompanyId(),
                        user.getCreatedAt(),
                        user.getUpdatedAt()))
            .toList();

    Map<String, Object> body = new HashMap<>();
    body.put("data", userDTOs);
    body.put("page", userPage.getNumber());
    body.put("size", userPage.getSize());
    body.put("totalElements", userPage.getTotalElements());
    body.put("totalPages", userPage.getTotalPages());
    body.put("hasNext", userPage.hasNext());
    body.put("hasPrevious", userPage.hasPrevious());
    return ResponseEntity.ok(body);
  }

  /**
   * Get user by ID.
   * Requires ADMIN or CHIEF_ACCOUNTANT role.
   */
  @GetMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN') or hasRole('CHIEF_ACCOUNTANT')")
  public ResponseEntity<Map<String, Object>> getUserById(@PathVariable Long id) {
    User user = userService.getUserById(id);
    UserDTO userDTO =
        new UserDTO(
            user.getId(),
            user.getEmail(),
            user.getFullName(),
            user.getRole(),
            user.getStatus(),
            user.getCompanyId(),
            user.getCreatedAt(),
            user.getUpdatedAt());

    Map<String, Object> body = new HashMap<>();
    body.put("data", userDTO);
    return ResponseEntity.ok(body);
  }

  /**
   * Create a new user with role assignment.
   * Requires ADMIN or CHIEF_ACCOUNTANT role.
   */
  @PostMapping
  @PreAuthorize("hasRole('ADMIN') or hasRole('CHIEF_ACCOUNTANT')")
  public ResponseEntity<Map<String, Object>> createUser(
      @Valid @RequestBody CreateUserRequest request, HttpServletRequest httpRequest) {
    User user = userService.createUser(request, httpRequest);
    UserDTO userDTO =
        new UserDTO(
            user.getId(),
            user.getEmail(),
            user.getFullName(),
            user.getRole(),
            user.getStatus(),
            user.getCompanyId(),
            user.getCreatedAt(),
            user.getUpdatedAt());

    Map<String, Object> body = new HashMap<>();
    body.put("data", userDTO);
    return ResponseEntity.created(URI.create("/api/v1/users/" + user.getId())).body(body);
  }

  /**
   * Update user role.
   * Requires ADMIN or CHIEF_ACCOUNTANT role.
   * Users cannot change their own role.
   */
  @PutMapping("/{id}/role")
  @PreAuthorize("hasRole('ADMIN') or hasRole('CHIEF_ACCOUNTANT')")
  public ResponseEntity<Map<String, Object>> updateUserRole(
      @PathVariable Long id,
      @Valid @RequestBody RoleUpdateRequest request,
      HttpServletRequest httpRequest) {

    // Get current user ID and role from security context
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    Long currentUserId = Long.parseLong(authentication.getPrincipal().toString());
    String token = extractTokenFromRequest(httpRequest);
    String currentUserRole = jwtTokenProvider.getRoleFromToken(token);

    User updatedUser =
        userService.updateUserRole(id, request.getRole(), currentUserId, currentUserRole, httpRequest);

    AuthResponse.UserResponse userResponse =
        new AuthResponse.UserResponse(
            updatedUser.getId(),
            updatedUser.getEmail(),
            updatedUser.getFullName(),
            updatedUser.getRole(),
            updatedUser.getCompanyId());

    Map<String, Object> body = new HashMap<>();
    body.put("data", userResponse);
    return ResponseEntity.ok(body);
  }

  /**
   * Update user (fullName, role, status).
   * Requires ADMIN or CHIEF_ACCOUNTANT role, or user editing own profile.
   */
  @PutMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN') or hasRole('CHIEF_ACCOUNTANT') or (authentication.principal == #id)")
  public ResponseEntity<Map<String, Object>> updateUser(
      @PathVariable Long id,
      @Valid @RequestBody UpdateUserRequest request,
      HttpServletRequest httpRequest) {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    Long currentUserId = Long.parseLong(authentication.getPrincipal().toString());
    String token = extractTokenFromRequest(httpRequest);
    String currentUserRole = jwtTokenProvider.getRoleFromToken(token);

    User updatedUser = userService.updateUser(id, request, currentUserId, currentUserRole, httpRequest);

    UserDTO userDTO =
        new UserDTO(
            updatedUser.getId(),
            updatedUser.getEmail(),
            updatedUser.getFullName(),
            updatedUser.getRole(),
            updatedUser.getStatus(),
            updatedUser.getCompanyId(),
            updatedUser.getCreatedAt(),
            updatedUser.getUpdatedAt());

    Map<String, Object> body = new HashMap<>();
    body.put("data", userDTO);
    return ResponseEntity.ok(body);
  }

  /**
   * Deactivate user.
   * Requires ADMIN or CHIEF_ACCOUNTANT role.
   */
  @PutMapping("/{id}/deactivate")
  @PreAuthorize("hasRole('ADMIN') or hasRole('CHIEF_ACCOUNTANT')")
  public ResponseEntity<Map<String, Object>> deactivateUser(
      @PathVariable Long id, HttpServletRequest httpRequest) {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    Long currentUserId = Long.parseLong(authentication.getPrincipal().toString());
    String token = extractTokenFromRequest(httpRequest);
    String currentUserRole = jwtTokenProvider.getRoleFromToken(token);

    User deactivatedUser = userService.deactivateUser(id, currentUserId, currentUserRole, httpRequest);

    UserDTO userDTO =
        new UserDTO(
            deactivatedUser.getId(),
            deactivatedUser.getEmail(),
            deactivatedUser.getFullName(),
            deactivatedUser.getRole(),
            deactivatedUser.getStatus(),
            deactivatedUser.getCompanyId(),
            deactivatedUser.getCreatedAt(),
            deactivatedUser.getUpdatedAt());

    Map<String, Object> body = new HashMap<>();
    body.put("data", userDTO);
    return ResponseEntity.ok(body);
  }

  /**
   * Activate user.
   * Requires ADMIN or CHIEF_ACCOUNTANT role.
   */
  @PutMapping("/{id}/activate")
  @PreAuthorize("hasRole('ADMIN') or hasRole('CHIEF_ACCOUNTANT')")
  public ResponseEntity<Map<String, Object>> activateUser(
      @PathVariable Long id, HttpServletRequest httpRequest) {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    Long currentUserId = Long.parseLong(authentication.getPrincipal().toString());
    String token = extractTokenFromRequest(httpRequest);
    String currentUserRole = jwtTokenProvider.getRoleFromToken(token);

    User activatedUser = userService.activateUser(id, currentUserId, currentUserRole, httpRequest);

    UserDTO userDTO =
        new UserDTO(
            activatedUser.getId(),
            activatedUser.getEmail(),
            activatedUser.getFullName(),
            activatedUser.getRole(),
            activatedUser.getStatus(),
            activatedUser.getCompanyId(),
            activatedUser.getCreatedAt(),
            activatedUser.getUpdatedAt());

    Map<String, Object> body = new HashMap<>();
    body.put("data", userDTO);
    return ResponseEntity.ok(body);
  }

  /**
   * Reset password by admin.
   * Requires ADMIN or CHIEF_ACCOUNTANT role.
   */
  @PostMapping("/{id}/reset-password")
  @PreAuthorize("hasRole('ADMIN') or hasRole('CHIEF_ACCOUNTANT')")
  public ResponseEntity<Map<String, Object>> resetPasswordByAdmin(
      @PathVariable Long id, HttpServletRequest httpRequest) {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    Long adminUserId = Long.parseLong(authentication.getPrincipal().toString());
    String token = extractTokenFromRequest(httpRequest);
    String adminUserRole = jwtTokenProvider.getRoleFromToken(token);

    userService.resetPasswordByAdmin(id, adminUserId, adminUserRole, httpRequest);

    Map<String, Object> body = new HashMap<>();
    body.put("message", "Password reset email sent");
    return ResponseEntity.ok(body);
  }

  /**
   * Get current user profile.
   * Requires authenticated user.
   */
  @GetMapping("/me")
  public ResponseEntity<Map<String, Object>> getCurrentUserProfile() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    Long userId = Long.parseLong(authentication.getPrincipal().toString());

    User user = userService.getCurrentUserProfile(userId);

    UserDTO userDTO =
        new UserDTO(
            user.getId(),
            user.getEmail(),
            user.getFullName(),
            user.getRole(),
            user.getStatus(),
            user.getCompanyId(),
            user.getCreatedAt(),
            user.getUpdatedAt());

    Map<String, Object> body = new HashMap<>();
    body.put("data", userDTO);
    return ResponseEntity.ok(body);
  }

  /**
   * Update current user profile (fullName only).
   * Requires authenticated user.
   */
  @PutMapping("/me")
  public ResponseEntity<Map<String, Object>> updateProfile(
      @Valid @RequestBody UpdateProfileRequest request, HttpServletRequest httpRequest) {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    Long userId = Long.parseLong(authentication.getPrincipal().toString());

    User updatedUser = userService.updateProfile(userId, request, httpRequest);

    UserDTO userDTO =
        new UserDTO(
            updatedUser.getId(),
            updatedUser.getEmail(),
            updatedUser.getFullName(),
            updatedUser.getRole(),
            updatedUser.getStatus(),
            updatedUser.getCompanyId(),
            updatedUser.getCreatedAt(),
            updatedUser.getUpdatedAt());

    Map<String, Object> body = new HashMap<>();
    body.put("data", userDTO);
    return ResponseEntity.ok(body);
  }

  /**
   * Change current user password.
   * Requires authenticated user.
   */
  @PostMapping("/me/change-password")
  public ResponseEntity<Map<String, Object>> changePassword(
      @Valid @RequestBody ChangePasswordRequest request, HttpServletRequest httpRequest) {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    Long userId = Long.parseLong(authentication.getPrincipal().toString());

    userService.changePassword(userId, request, httpRequest);

    Map<String, Object> body = new HashMap<>();
    body.put("message", "Password changed successfully");
    return ResponseEntity.ok(body);
  }

  private String extractTokenFromRequest(HttpServletRequest request) {
    String bearerToken = request.getHeader("Authorization");
    if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
      return bearerToken.substring(7);
    }
    return null;
  }
}
