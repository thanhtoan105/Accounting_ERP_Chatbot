package com.accounting.exception;

import com.accounting.dto.VoucherValidationResult;

/**
 * Exception thrown when voucher validation fails.
 * Carries the validation result with field-level error details.
 */
public class VoucherValidationException extends RuntimeException {
  private final VoucherValidationResult validationResult;

  public VoucherValidationException(VoucherValidationResult validationResult) {
    super("Voucher validation failed");
    this.validationResult = validationResult;
  }

  public VoucherValidationException(String message, VoucherValidationResult validationResult) {
    super(message);
    this.validationResult = validationResult;
  }

  public VoucherValidationResult getValidationResult() {
    return validationResult;
  }
}

