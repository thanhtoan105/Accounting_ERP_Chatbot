package com.accounting.exception;

import java.util.Map;

/**
 * Exception thrown when voucher posting fails due to validation errors.
 * Contains detailed error map for client-side display.
 */
public class VoucherPostingException extends RuntimeException {

  private final Map<String, Object> validationErrors;

  public VoucherPostingException(String message, Map<String, Object> validationErrors) {
    super(message);
    this.validationErrors = validationErrors;
  }

  public Map<String, Object> getValidationErrors() {
    return validationErrors;
  }
}
