package com.accounting.dto;

import java.util.HashMap;
import java.util.Map;

/**
 * DTO for voucher validation results.
 * Contains validation status and field-level error messages.
 */
public class VoucherValidationResult {

  private boolean valid;
  private Map<Integer, Map<String, String>> errors; // lineNumber -> { field -> error message }

  public VoucherValidationResult() {
    this.valid = true;
    this.errors = new HashMap<>();
  }

  public VoucherValidationResult(boolean valid, Map<Integer, Map<String, String>> errors) {
    this.valid = valid;
    this.errors = errors != null ? errors : new HashMap<>();
  }

  /**
   * Add an error for a specific line and field.
   *
   * @param lineNumber line number (0-based or 1-based based on convention)
   * @param field field name
   * @param message error message
   */
  public void addError(Integer lineNumber, String field, String message) {
    if (this.errors == null) {
      this.errors = new HashMap<>();
    }
    this.errors.computeIfAbsent(lineNumber, k -> new HashMap<>()).put(field, message);
    this.valid = false;
  }

  /**
   * Add multiple errors for a specific line.
   *
   * @param lineNumber line number
   * @param lineErrors map of field -> error message
   */
  public void addLineErrors(Integer lineNumber, Map<String, String> lineErrors) {
    if (this.errors == null) {
      this.errors = new HashMap<>();
    }
    if (lineErrors != null && !lineErrors.isEmpty()) {
      this.errors.put(lineNumber, lineErrors);
      this.valid = false;
    }
  }

  // Getters and setters
  public boolean isValid() {
    return valid;
  }

  public void setValid(boolean valid) {
    this.valid = valid;
  }

  public Map<Integer, Map<String, String>> getErrors() {
    return errors;
  }

  public void setErrors(Map<Integer, Map<String, String>> errors) {
    this.errors = errors;
    // Update valid status based on errors
    this.valid = (errors == null || errors.isEmpty());
  }
}

