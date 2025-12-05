package com.accounting.dto.report;

/**
 * DTO for validation errors in statutory reports.
 * Matches the frontend ValidationError interface.
 */
public class ValidationErrorDTO {

  public enum ErrorType {
    NULL_VALUE,
    IMBALANCE,
    MISSING_MAPPING,
    GENERATION_FAILED
  }

  private String lineCode;
  private String lineName;
  private ErrorType errorType;
  private String message;
  private String suggestion;

  public ValidationErrorDTO() {}

  public ValidationErrorDTO(
      String lineCode, String lineName, ErrorType errorType, String message, String suggestion) {
    this.lineCode = lineCode;
    this.lineName = lineName;
    this.errorType = errorType;
    this.message = message;
    this.suggestion = suggestion;
  }

  // Factory methods for common error types
  public static ValidationErrorDTO nullValue(String lineCode, String lineName) {
    return new ValidationErrorDTO(
        lineCode,
        lineName,
        ErrorType.NULL_VALUE,
        "Line has NULL value",
        "Check if account mappings are configured for this line");
  }

  public static ValidationErrorDTO imbalance(String assetValue, String liabilityEquityValue) {
    return new ValidationErrorDTO(
        "270/440",
        "Balance Check",
        ErrorType.IMBALANCE,
        String.format(
            "Balance Sheet is not balanced: Assets (%s) != Liabilities + Equity (%s)",
            assetValue, liabilityEquityValue),
        "Review all journal entries to ensure debits equal credits");
  }

  public static ValidationErrorDTO generationFailed(String message) {
    return new ValidationErrorDTO(
        null,
        null,
        ErrorType.GENERATION_FAILED,
        "Failed to generate report: " + message,
        "Check period configuration and report mappings");
  }

  // Getters and setters
  public String getLineCode() {
    return lineCode;
  }

  public void setLineCode(String lineCode) {
    this.lineCode = lineCode;
  }

  public String getLineName() {
    return lineName;
  }

  public void setLineName(String lineName) {
    this.lineName = lineName;
  }

  public ErrorType getErrorType() {
    return errorType;
  }

  public void setErrorType(ErrorType errorType) {
    this.errorType = errorType;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public String getSuggestion() {
    return suggestion;
  }

  public void setSuggestion(String suggestion) {
    this.suggestion = suggestion;
  }
}
