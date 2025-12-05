package com.accounting.dto.report;

import java.util.List;

/**
 * DTO for report validation results.
 * Matches the frontend ValidationResult interface.
 */
public class ValidationResultDTO {

  private boolean isValid;
  private List<ValidationErrorDTO> errors;
  private List<String> warnings;
  private String periodStatus; // "OPEN" or "CLOSED"
  private boolean isBalanced;

  public ValidationResultDTO() {}

  public ValidationResultDTO(
      boolean isValid,
      List<ValidationErrorDTO> errors,
      List<String> warnings,
      String periodStatus,
      boolean isBalanced) {
    this.isValid = isValid;
    this.errors = errors;
    this.warnings = warnings;
    this.periodStatus = periodStatus;
    this.isBalanced = isBalanced;
  }

  // Getters and setters
  public boolean isValid() {
    return isValid;
  }

  public void setValid(boolean valid) {
    isValid = valid;
  }

  public List<ValidationErrorDTO> getErrors() {
    return errors;
  }

  public void setErrors(List<ValidationErrorDTO> errors) {
    this.errors = errors;
  }

  public List<String> getWarnings() {
    return warnings;
  }

  public void setWarnings(List<String> warnings) {
    this.warnings = warnings;
  }

  public String getPeriodStatus() {
    return periodStatus;
  }

  public void setPeriodStatus(String periodStatus) {
    this.periodStatus = periodStatus;
  }

  public boolean isBalanced() {
    return isBalanced;
  }

  public void setBalanced(boolean balanced) {
    isBalanced = balanced;
  }
}
