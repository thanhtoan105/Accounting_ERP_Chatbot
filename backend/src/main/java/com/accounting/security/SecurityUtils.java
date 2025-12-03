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

  /**
   * Get current user email from security context.
   *
   * @return current user email, or null if not available
   */
  public static String getCurrentUserEmail() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null) {
      return null;
    }
    // The email is typically stored in the authentication name or credentials
    String name = authentication.getName();
    if (name != null && name.contains("@")) {
      return name;
    }
    return null;
  }

  /**
   * Get current IP address from request context.
   *
   * @return current IP address, or null if not available
   */
  public static String getCurrentIpAddress() {
    // FIXME(story-6-6): Implement IP address extraction for audit logging (AC6.6-01)
    // Options:
    // 1. Use RequestContextHolder.currentRequestAttributes() to get HttpServletRequest
    // 2. Create IpAddressContext ThreadLocal similar to CompanyContext
    // 3. Store in SecurityContext custom attributes
    // Currently returns null, which means PERIOD_BLOCK_ATTEMPT logs have no IP address
    return null;
  }
}
