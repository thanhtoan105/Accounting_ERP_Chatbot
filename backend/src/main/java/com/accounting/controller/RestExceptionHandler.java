package com.accounting.controller;

import com.accounting.exception.CompanyScopeViolationException;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ValidationException;
import java.util.HashMap;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.server.ResponseStatusException;

@ControllerAdvice
public class RestExceptionHandler {

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<Map<String, Object>> handleBeanValidation(MethodArgumentNotValidException ex) {
    Map<String, Object> details = new HashMap<>();
    ex.getBindingResult()
        .getFieldErrors()
        .forEach(err -> details.put(err.getField(), err.getDefaultMessage()));
    return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Validation failed", details);
  }

  @ExceptionHandler(ValidationException.class)
  public ResponseEntity<Map<String, Object>> handleValidation(ValidationException ex) {
    String msg = ex.getMessage() == null ? "Validation failed" : ex.getMessage();
    if (msg.startsWith("DUPLICATE")) {
      // Normalize to 409 with simple field hint if present
      Map<String, Object> details = new HashMap<>();
      if (msg.contains("code")) {
        details.put("field", "code");
      } else if (msg.contains("tax_code")) {
        details.put("field", "tax_code");
      } else if (msg.contains("email")) {
        details.put("field", "email");
      }
      return build(HttpStatus.CONFLICT, "DUPLICATE", msg, details);
    }
    Map<String, Object> details = new HashMap<>();
    return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", msg, details);
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<Map<String, Object>> handleConstraint(DataIntegrityViolationException ex) {
    // Fall back to 409 for unique constraint violations
    Map<String, Object> details = new HashMap<>();
    String lower = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase();
    if (lower.contains("ux_companies_code")) {
      details.put("field", "code");
    } else if (lower.contains("ux_companies_tax_code")) {
      details.put("field", "tax_code");
    }
    return build(HttpStatus.CONFLICT, "DUPLICATE", "Unique constraint violation", details);
  }

  @ExceptionHandler(CompanyScopeViolationException.class)
  public ResponseEntity<Map<String, Object>> handleCompanyScope(CompanyScopeViolationException ex) {
    Map<String, Object> details = new HashMap<>();
    return build(HttpStatus.FORBIDDEN, "RBAC_COMPANY_SCOPE_VIOLATION", ex.getMessage(), details);
  }

  @ExceptionHandler(EntityNotFoundException.class)
  public ResponseEntity<Map<String, Object>> handleNotFound(EntityNotFoundException ex) {
    return build(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage(), new HashMap<>());
  }

  @ExceptionHandler(ResponseStatusException.class)
  public ResponseEntity<Map<String, Object>> handleResponseStatus(ResponseStatusException ex) {
    HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
    Map<String, Object> details = new HashMap<>();
    
    String reason = ex.getReason() != null ? ex.getReason() : "";
    
    if (status == HttpStatus.LOCKED) {
      return build(status, "ACCOUNT_LOCKED", reason.isEmpty() ? "Account is locked" : reason, details);
    } else if (status == HttpStatus.UNAUTHORIZED) {
      // Check for specific messages to provide more specific error codes
      String reasonLower = reason.toLowerCase();
      if (reasonLower.contains("deactivated") || reasonLower.contains("account is deactivated")) {
        return build(status, "ACCOUNT_DEACTIVATED", reason.isEmpty() ? "Account is deactivated" : reason, details);
      }
      return build(status, "UNAUTHORIZED", reason.isEmpty() ? "Unauthorized" : reason, details);
    }
    
    return build(status, "ERROR", reason.isEmpty() ? "An error occurred" : reason, details);
  }

  @ExceptionHandler({AccessDeniedException.class, AuthorizationDeniedException.class})
  public ResponseEntity<Map<String, Object>> handleAccessDenied(Exception ex, jakarta.servlet.http.HttpServletRequest request) {
    // Extract role information from exception message if available
    String message = "Access denied. Insufficient permissions.";
    
    // Try to extract from exception message, cause, and stack trace
    String exceptionMessage = ex.getMessage();
    if (exceptionMessage == null) {
      exceptionMessage = ex.toString();
    }
    
    // Also check the exception's cause and stack trace for annotation info
    Throwable cause = ex.getCause();
    if (cause != null) {
      String causeMsg = cause.getMessage();
      if (causeMsg != null && (causeMsg.contains("hasRole") || causeMsg.contains("ADMIN") || causeMsg.contains("CHIEF_ACCOUNTANT"))) {
        exceptionMessage = causeMsg;
      }
    }
    
    // Check full stack trace string for annotation patterns
    String fullStackTrace = "";
    try {
      java.io.StringWriter sw = new java.io.StringWriter();
      java.io.PrintWriter pw = new java.io.PrintWriter(sw);
      ex.printStackTrace(pw);
      fullStackTrace = sw.toString();
    } catch (Exception e) {
      // Ignore
    }
    
    if (exceptionMessage != null || !fullStackTrace.isEmpty()) {
      String searchText = exceptionMessage != null ? exceptionMessage + " " + fullStackTrace : fullStackTrace;
      
      // Try to extract role from Spring Security messages
      // Pattern: hasRole('ADMIN') or hasRole('CHIEF_ACCOUNTANT')
      java.util.regex.Pattern multiRolePattern = java.util.regex.Pattern.compile("hasRole\\(['\"](\\w+)['\"]\\)\\s+or\\s+hasRole\\(['\"](\\w+)['\"]\\)");
      java.util.regex.Matcher multiRoleMatcher = multiRolePattern.matcher(searchText);
      if (multiRoleMatcher.find()) {
        String firstRole = multiRoleMatcher.group(1);
        String secondRole = multiRoleMatcher.group(2);
        message = "Access denied. Required role: " + firstRole + " or " + secondRole;
      } else {
        // Handle single hasRole('ADMIN')
        java.util.regex.Pattern hasRolePattern = java.util.regex.Pattern.compile("hasRole\\(['\"](\\w+)['\"]\\)");
        java.util.regex.Matcher hasRoleMatcher = hasRolePattern.matcher(searchText);
        if (hasRoleMatcher.find()) {
          String requiredRole = hasRoleMatcher.group(1);
          message = "Access denied. Required role: " + requiredRole;
        } else if (searchText.contains("ADMIN") || searchText.contains("CHIEF_ACCOUNTANT")) {
          // Fallback: if we see role names, provide appropriate message
          if (searchText.contains("ADMIN") && searchText.contains("CHIEF_ACCOUNTANT")) {
            message = "Access denied. Required role: ADMIN or CHIEF_ACCOUNTANT";
          } else if (searchText.contains("ADMIN")) {
            message = "Access denied. Required role: ADMIN";
          } else if (searchText.contains("CHIEF_ACCOUNTANT")) {
            message = "Access denied. Required role: CHIEF_ACCOUNTANT";
          }
        }
      }
    }
    
    // If still no role info, check request URI to provide endpoint-specific message
    if (message.equals("Access denied. Insufficient permissions.") && request != null) {
      String uri = request.getRequestURI();
      if (uri != null) {
        if (uri.contains("/users")) {
          message = "Access denied. Required role: ADMIN or CHIEF_ACCOUNTANT";
        } else if (uri.contains("/invitations")) {
          message = "Access denied. Required role: ADMIN or CHIEF_ACCOUNTANT";
        }
      }
    }
    
    Map<String, Object> details = new HashMap<>();
    return build(HttpStatus.FORBIDDEN, "FORBIDDEN", message, details);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
    return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Unexpected error", new HashMap<>());
  }

  private ResponseEntity<Map<String, Object>> build(
      HttpStatus status, String code, String message, Map<String, Object> details) {
    Map<String, Object> body = new HashMap<>();
    Map<String, Object> err = new HashMap<>();
    err.put("code", code);
    err.put("message", message);
    if (details != null && !details.isEmpty()) {
      err.put("details", details);
    }
    body.put("error", err);
    return ResponseEntity.status(status).body(body);
  }
}


