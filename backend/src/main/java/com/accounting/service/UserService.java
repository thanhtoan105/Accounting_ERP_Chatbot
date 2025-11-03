package com.accounting.service;

import com.accounting.dto.ChangePasswordRequest;
import com.accounting.dto.CreateUserRequest;
import com.accounting.dto.UpdateProfileRequest;
import com.accounting.dto.UpdateUserRequest;
import com.accounting.entity.User;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Service for user management operations including role assignment.
 */
public interface UserService {

  /**
   * Get all users in the current company.
   *
   * @return list of users
   */
  List<User> getAllUsers();

  /**
   * Find users with filtering by role, status, and search term.
   * Company-scoped - only returns users in the current company.
   *
   * @param role filter by role (optional, null to ignore)
   * @param status filter by status (optional, null to ignore)
   * @param search search term for email or full name (optional, null to ignore)
   * @param pageable pagination parameters
   * @return paginated list of users
   */
  Page<User> findAllWithFilters(String role, String status, String search, Pageable pageable);

  /**
   * Get user by ID (must be in same company).
   *
   * @param userId user ID
   * @return user or null if not found
   */
  User getUserById(Long userId);

  /**
   * Create a new user with role assignment.
   *
   * @param request create user request
   * @param httpRequest HTTP request for audit logging
   * @return created user
   */
  User createUser(CreateUserRequest request, HttpServletRequest httpRequest);

  /**
   * Update user (fullName, role, status). Only ADMIN or CHIEF_ACCOUNTANT can change roles/status.
   * User can edit own fullName but not role or status.
   *
   * @param userId target user ID
   * @param request update user request
   * @param currentUserId current user ID (for self-change prevention)
   * @param currentUserRole current user role (for authorization check)
   * @param httpRequest HTTP request for audit logging
   * @return updated user
   */
  User updateUser(
      Long userId,
      UpdateUserRequest request,
      Long currentUserId,
      String currentUserRole,
      HttpServletRequest httpRequest);

  /**
   * Update user role. Only ADMIN or CHIEF_ACCOUNTANT can change roles.
   * User cannot change their own role.
   *
   * @param userId target user ID
   * @param newRole new role value
   * @param currentUserId current user ID (for self-change prevention)
   * @param currentUserRole current user role (for authorization check)
   * @param httpRequest HTTP request for audit logging
   * @return updated user
   */
  User updateUserRole(
      Long userId,
      String newRole,
      Long currentUserId,
      String currentUserRole,
      HttpServletRequest httpRequest);

  /**
   * Deactivate user. Only ADMIN or CHIEF_ACCOUNTANT can deactivate users.
   * Only ADMIN can deactivate ADMIN users.
   *
   * @param userId target user ID
   * @param currentUserId current user ID (for self-change prevention)
   * @param currentUserRole current user role (for role hierarchy check)
   * @param httpRequest HTTP request for audit logging
   * @return deactivated user
   */
  User deactivateUser(Long userId, Long currentUserId, String currentUserRole, HttpServletRequest httpRequest);

  /**
   * Activate user. Only ADMIN or CHIEF_ACCOUNTANT can activate users.
   * Only ADMIN can activate ADMIN users.
   *
   * @param userId target user ID
   * @param currentUserId current user ID
   * @param currentUserRole current user role (for role hierarchy check)
   * @param httpRequest HTTP request for audit logging
   * @return activated user
   */
  User activateUser(Long userId, Long currentUserId, String currentUserRole, HttpServletRequest httpRequest);

  /**
   * Reset password by admin. Only ADMIN or CHIEF_ACCOUNTANT can reset passwords.
   * Only ADMIN can reset password for ADMIN users.
   *
   * @param userId target user ID
   * @param adminUserId admin user ID who initiated the reset
   * @param adminUserRole admin user role (for role hierarchy check)
   * @param httpRequest HTTP request for audit logging
   */
  void resetPasswordByAdmin(Long userId, Long adminUserId, String adminUserRole, HttpServletRequest httpRequest);

  /**
   * Get current user profile.
   *
   * @param userId current user ID
   * @return current user
   */
  User getCurrentUserProfile(Long userId);

  /**
   * Update current user profile (fullName only).
   *
   * @param userId current user ID
   * @param request update profile request
   * @param httpRequest HTTP request for audit logging
   * @return updated user
   */
  User updateProfile(Long userId, UpdateProfileRequest request, HttpServletRequest httpRequest);

  /**
   * Change current user password.
   *
   * @param userId current user ID
   * @param request change password request
   * @param httpRequest HTTP request for audit logging
   */
  void changePassword(Long userId, ChangePasswordRequest request, HttpServletRequest httpRequest);
}

