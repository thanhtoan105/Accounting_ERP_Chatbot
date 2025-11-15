package com.accounting.dto;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DTO for voucher validation results.
 * Contains validation status and field-level error messages.
 */
public class VoucherValidationResult {

  private boolean valid;
  private Map<Integer, Map<String, List<String>>> errors; // lineNumber -> { field -> error messages }

  public VoucherValidationResult() {
    this.valid = true;
    this.errors = new HashMap<>();
  }

  public VoucherValidationResult(
      boolean valid, Map<Integer, Map<String, List<String>>> errors) {
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
    if (lineNumber == null) {
      lineNumber = 0;
    }
    this.errors
        .computeIfAbsent(lineNumber, k -> new HashMap<>())
        .computeIfAbsent(field, k -> new ArrayList<>())
        .add(message);
    this.valid = false;
  }

  /**
   * Add multiple errors for a specific line.
   *
   * @param lineNumber line number
   * @param lineErrors map of field -> error message
   */
  public void addLineErrors(Integer lineNumber, Map<String, List<String>> lineErrors) {
    if (lineErrors == null || lineErrors.isEmpty()) {
      return;
    }
    lineErrors.forEach(
        (field, messages) -> {
          if (messages == null || messages.isEmpty()) {
            return;
          }
          messages.forEach(message -> addError(lineNumber, field, message));
        });
  }

  // Getters and setters
  public boolean isValid() {
    return valid;
  }

  public void setValid(boolean valid) {
    this.valid = valid;
  }

  public Map<Integer, Map<String, List<String>>> getErrors() {
    return errors;
  }

  public void setErrors(Map<Integer, Map<String, List<String>>> errors) {
    this.errors = errors;
    // Update valid status based on errors
    this.valid = (errors == null || errors.isEmpty());
  }

  /**
   * Helper to determine whether a specific line currently has validation errors attached.
   *
   * @param lineNumber line number to inspect
   * @return true if one or more errors exist for the line
   */
  public boolean hasErrorsForLine(int lineNumber) {
    if (errors == null || errors.isEmpty()) {
      return false;
    }
    Map<String, List<String>> lineErrors = errors.get(lineNumber);
    return lineErrors != null && lineErrors.values().stream().anyMatch(list -> list != null && !list.isEmpty());
  }
}

