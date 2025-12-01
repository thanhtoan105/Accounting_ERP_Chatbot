package com.accounting.dto;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DTO for receipt validation results with field-level errors.
 */
public class ReceiptValidationResult {

  private boolean valid;
  private Map<String, List<String>> fieldErrors;
  private List<String> globalErrors;

  public ReceiptValidationResult() {
    this.valid = true;
    this.fieldErrors = new HashMap<>();
    this.globalErrors = new ArrayList<>();
  }

  public void addFieldError(String field, String message) {
    this.valid = false;
    fieldErrors.computeIfAbsent(field, k -> new ArrayList<>()).add(message);
  }

  public void addGlobalError(String message) {
    this.valid = false;
    globalErrors.add(message);
  }

  public boolean isValid() {
    return valid;
  }

  public void setValid(boolean valid) {
    this.valid = valid;
  }

  public Map<String, List<String>> getFieldErrors() {
    return fieldErrors;
  }

  public void setFieldErrors(Map<String, List<String>> fieldErrors) {
    this.fieldErrors = fieldErrors;
  }

  public List<String> getGlobalErrors() {
    return globalErrors;
  }

  public void setGlobalErrors(List<String> globalErrors) {
    this.globalErrors = globalErrors;
  }

  public boolean hasErrors() {
    return !valid;
  }
}
