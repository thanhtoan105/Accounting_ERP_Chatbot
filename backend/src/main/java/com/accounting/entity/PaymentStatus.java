package com.accounting.entity;

/**
 * Enumeration representing the status of an AP payment.
 */
public enum PaymentStatus {
  /**
   * Payment is in draft state and can be edited/deleted by creator.
   */
  DRAFT("Draft"),

  /**
   * Payment is pending approval (for high-value payments).
   */
  PENDING_APPROVAL("Pending Approval"),

  /**
   * Payment has been posted to the general ledger.
   */
  POSTED("Posted"),

  /**
   * Payment has been cancelled.
   */
  CANCELLED("Cancelled");

  private final String displayName;

  PaymentStatus(String displayName) {
    this.displayName = displayName;
  }

  public String getDisplayName() {
    return displayName;
  }
}

