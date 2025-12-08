package com.accounting.dto;

import java.util.HashMap;
import java.util.Map;

/**
 * DTO for payment validation results with field-level error mapping.
 * Follows the same pattern as PurchaseBillValidationResult.
 */
public class PaymentValidationResult {

  private boolean valid;
  private Map<String, String> fieldErrors;
  private Map<String, String> warnings;

  public PaymentValidationResult() {
    this.valid = true;
    this.fieldErrors = new HashMap<>();
    this.warnings = new HashMap<>();
  }

  public PaymentValidationResult(boolean valid) {
    this.valid = valid;
    this.fieldErrors = new HashMap<>();
    this.warnings = new HashMap<>();
  }

  public void addFieldError(String field, String message) {
    this.valid = false;
    this.fieldErrors.put(field, message);
  }

  public void addWarning(String field, String message) {
    this.warnings.put(field, message);
  }

  public boolean isValid() {
    return valid;
  }

  public void setValid(boolean valid) {
    this.valid = valid;
  }

  public Map<String, String> getFieldErrors() {
    return fieldErrors;
  }

  public void setFieldErrors(Map<String, String> fieldErrors) {
    this.fieldErrors = fieldErrors;
  }

  public Map<String, String> getWarnings() {
    return warnings;
  }

  public void setWarnings(Map<String, String> warnings) {
    this.warnings = warnings;
  }

  public boolean hasErrors() {
    return !fieldErrors.isEmpty();
  }

  public boolean hasWarnings() {
    return !warnings.isEmpty();
  }
}
