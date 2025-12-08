package com.accounting.dto;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DTO for purchase bill validation results.
 * Contains validation status and field-level error messages.
 */
public class PurchaseBillValidationResult {

  private boolean valid;
  private Map<String, List<String>> headerErrors; // Header field -> error messages
  private Map<Integer, Map<String, List<String>>> lineErrors; // lineNumber -> { field -> error messages }

  public PurchaseBillValidationResult() {
    this.valid = true;
    this.headerErrors = new HashMap<>();
    this.lineErrors = new HashMap<>();
  }

  public PurchaseBillValidationResult(
      boolean valid,
      Map<String, List<String>> headerErrors,
      Map<Integer, Map<String, List<String>>> lineErrors) {
    this.valid = valid;
    this.headerErrors = headerErrors != null ? headerErrors : new HashMap<>();
    this.lineErrors = lineErrors != null ? lineErrors : new HashMap<>();
  }

  /**
   * Add an error for a header field.
   *
   * @param field field name
   * @param message error message
   */
  public void addHeaderError(String field, String message) {
    this.headerErrors
        .computeIfAbsent(field, k -> new ArrayList<>())
        .add(message);
    this.valid = false;
  }

  /**
   * Add an error for a specific line and field.
   *
   * @param lineNumber line number (1-based)
   * @param field field name
   * @param message error message
   */
  public void addLineError(Integer lineNumber, String field, String message) {
    if (lineNumber == null) {
      lineNumber = 0;
    }
    this.lineErrors
        .computeIfAbsent(lineNumber, k -> new HashMap<>())
        .computeIfAbsent(field, k -> new ArrayList<>())
        .add(message);
    this.valid = false;
  }

  /**
   * Add multiple errors for a specific line.
   *
   * @param lineNumber line number
   * @param lineErrors map of field -> error messages
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
          messages.forEach(message -> addLineError(lineNumber, field, message));
        });
  }

  // Getters and setters
  public boolean isValid() {
    return valid;
  }

  public void setValid(boolean valid) {
    this.valid = valid;
  }

  public Map<String, List<String>> getHeaderErrors() {
    return headerErrors;
  }

  public void setHeaderErrors(Map<String, List<String>> headerErrors) {
    this.headerErrors = headerErrors != null ? headerErrors : new HashMap<>();
    // Update valid status based on errors
    this.valid = (this.headerErrors.isEmpty() && this.lineErrors.isEmpty());
  }

  public Map<Integer, Map<String, List<String>>> getLineErrors() {
    return lineErrors;
  }

  public void setLineErrors(Map<Integer, Map<String, List<String>>> lineErrors) {
    this.lineErrors = lineErrors != null ? lineErrors : new HashMap<>();
    // Update valid status based on errors
    this.valid = (this.headerErrors.isEmpty() && this.lineErrors.isEmpty());
  }

  /**
   * Helper to determine whether a specific line currently has validation errors attached.
   *
   * @param lineNumber line number to inspect
   * @return true if one or more errors exist for the line
   */
  public boolean hasErrorsForLine(int lineNumber) {
    if (lineErrors == null || lineErrors.isEmpty()) {
      return false;
    }
    Map<String, List<String>> errors = lineErrors.get(lineNumber);
    return errors != null && errors.values().stream().anyMatch(list -> list != null && !list.isEmpty());
  }

  /**
   * Check if there are any header errors.
   *
   * @return true if one or more header errors exist
   */
  public boolean hasHeaderErrors() {
    return headerErrors != null && !headerErrors.isEmpty();
  }
}
