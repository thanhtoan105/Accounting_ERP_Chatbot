package com.accounting.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

/**
 * Utility class for security context operations.
 * Provides shared methods for extracting user information from security context.
 */
public class SecurityUtils {

  private static final Logger logger = LoggerFactory.getLogger(SecurityUtils.class);

  /**
   * Get current user ID from security context.
   *
   * @return current user ID
   * @throws ResponseStatusException if user cannot be determined (401 Unauthorized)
   */
  public static Long getCurrentUserId() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || authentication.getPrincipal() == null) {
      throw new ResponseStatusException(
          HttpStatus.UNAUTHORIZED, "Unable to determine current user");
    }
    try {
      return Long.parseLong(authentication.getPrincipal().toString());
    } catch (NumberFormatException e) {
      logger.error("Failed to parse user ID from authentication principal", e);
      throw new ResponseStatusException(
          HttpStatus.UNAUTHORIZED, "Unable to determine current user");
    }
  }
}

