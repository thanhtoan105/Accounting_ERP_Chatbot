package com.accounting.entity;

/**
 * Enumeration representing the status of an accounting period.
 */
public enum PeriodStatus {
  /**
   * Period is open for voucher creation and posting.
   */
  OPEN("Open"),

  /**
   * Period is closed and no new vouchers can be created or posted.
   */
  CLOSED("Closed");

  private final String displayName;

  PeriodStatus(String displayName) {
    this.displayName = displayName;
  }

  public String getDisplayName() {
    return displayName;
  }
}