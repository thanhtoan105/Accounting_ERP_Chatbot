package com.accounting.service;

import com.accounting.enums.Role;

/**
 * Service for role validation and management utilities.
 */
public interface RoleService {

  /**
   * Validate that a role string is a valid Role enum value.
   *
   * @param role the role string to validate
   * @return true if valid, false otherwise
   */
  boolean isValidRole(String role);

  /**
   * Convert string role to Role enum.
   *
   * @param role the role string
   * @return Role enum or null if invalid
   */
  Role toRoleEnum(String role);

  /**
   * Get default role for new users.
   *
   * @return default Role enum (ACCOUNTANT)
   */
  Role getDefaultRole();

  /**
   * Convert Role enum to Spring Security authority format (ROLE_ADMIN, etc.).
   *
   * @param role the Role enum
   * @return Spring Security authority string
   */
  String toAuthority(Role role);
}

