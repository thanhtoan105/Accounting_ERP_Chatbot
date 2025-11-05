package com.accounting.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * Custom access denied handler that returns context-aware 403 error messages.
 * Includes information about which role is required for the requested action.
 */
@Component
public class CustomAccessDeniedHandler implements AccessDeniedHandler {

  private final ObjectMapper objectMapper;

  public CustomAccessDeniedHandler(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public void handle(
      HttpServletRequest request,
      HttpServletResponse response,
      AccessDeniedException accessDeniedException)
      throws IOException {

    // Extract required role from exception message or determine from request
    String requiredRole = extractRequiredRole(accessDeniedException, request);
    String errorMessage =
        requiredRole != null
            ? "Access denied. Required role: " + requiredRole
            : "Access denied. Insufficient permissions.";

    // Build error response following the existing pattern
    Map<String, Object> errorResponse =
        Map.of(
            "error",
            Map.of(
                "code", "FORBIDDEN",
                "message", errorMessage),
            "meta",
            Map.of(
                "timestamp", Instant.now().toString(),
                "path", request.getRequestURI(),
                "method", request.getMethod()));

    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding("UTF-8");
    objectMapper.writeValue(response.getWriter(), errorResponse);
  }

  /**
   * Extract required role from exception message or determine from authentication context.
   */
  private String extractRequiredRole(AccessDeniedException exception, HttpServletRequest request) {
    // Try to extract from exception message (Spring Security may include role info)
    String exceptionMessage = exception.getMessage();
    if (exceptionMessage != null) {
      // Check for common patterns in Spring Security messages
      if (exceptionMessage.contains("hasRole('") || exceptionMessage.contains("hasRole(\"")) {
        // Extract role from message pattern like "hasRole('ADMIN')"
        int start = exceptionMessage.indexOf("hasRole('");
        if (start == -1) {
          start = exceptionMessage.indexOf("hasRole(\"");
        }
        if (start != -1) {
          int roleStart = start + 9; // length of "hasRole('"
          int roleEnd = exceptionMessage.indexOf("'", roleStart);
          if (roleEnd == -1) {
            roleEnd = exceptionMessage.indexOf("\"", roleStart);
          }
          if (roleEnd > roleStart) {
            return exceptionMessage.substring(roleStart, roleEnd);
          }
        }
      }
    }

    // Fallback: Could try to determine from endpoint patterns
    // For now, return generic message
    return null;
  }
}

