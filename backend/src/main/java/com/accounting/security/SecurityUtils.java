package com.accounting.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

/**
 * Utility class for security context operations.
 * Provides shared methods for extracting user information from security
 * context.
 */
public class SecurityUtils {

  private static final Logger logger = LoggerFactory.getLogger(SecurityUtils.class);

  /**
   * Get current user ID from security context.
   *
   * @return current user ID
   * @throws ResponseStatusException if user cannot be determined (401
   *                                 Unauthorized)
   */
  public static Long getCurrentUserId() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || authentication.getPrincipal() == null) {
      throw new ResponseStatusException(
          HttpStatus.UNAUTHORIZED, "Unable to determine current user");
    }

    Object principal = authentication.getPrincipal();

    // Handle Long principal directly (from JWT filter)
    if (principal instanceof Long) {
      return (Long) principal;
    }

    // Handle String principal (fallback for other authentication mechanisms)
    try {
      return Long.parseLong(principal.toString());
    } catch (NumberFormatException e) {
      logger.error("Failed to parse user ID from authentication principal: {}", principal, e);
      throw new ResponseStatusException(
          HttpStatus.UNAUTHORIZED, "Unable to determine current user");
    }
  }

  /**
   * Check if current user has specific role.
   *
   * @param role role name (without ROLE_ prefix)
   * @return true if user has role
   */
  public static boolean hasRole(String role) {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null) {
      return false;
    }
    return authentication.getAuthorities().stream()
        .anyMatch(a -> a.getAuthority().equals("ROLE_" + role));
  }
}
