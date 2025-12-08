package com.accounting.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a request violates company scope (cross-company access).
 */
public class CompanyScopeViolationException extends RuntimeException {
  private final HttpStatus status = HttpStatus.FORBIDDEN;

  public CompanyScopeViolationException(String message) {
    super(message);
  }

  public HttpStatus getStatus() {
    return status;
  }
}
