package com.accounting.enums;

/**
 * Enum representing the type of financial report.
 * Used for generating Balance Sheet and Income Statement reports.
 */
public enum ReportType {
  BALANCE_SHEET("balance_sheet", "Balance Sheet"),
  INCOME_STATEMENT("income_statement", "Income Statement");

  private final String value;
  private final String displayName;

  ReportType(String value, String displayName) {
    this.value = value;
    this.displayName = displayName;
  }

  public String getValue() {
    return value;
  }

  public String getDisplayName() {
    return displayName;
  }

  /**
   * Convert string value to ReportType enum. Case-sensitive.
   *
   * @param value the report type string value
   * @return ReportType enum or null if not found
   */
  public static ReportType fromString(String value) {
    if (value == null) {
      return null;
    }
    for (ReportType type : ReportType.values()) {
      if (type.value.equals(value)) {
        return type;
      }
    }
    return null;
  }

  /**
   * Check if a string value is a valid report type.
   *
   * @param value the report type string value
   * @return true if valid, false otherwise
   */
  public static boolean isValid(String value) {
    return fromString(value) != null;
  }
}
