package com.accounting.entity;

/**
 * Enumeration representing the status of a purchase bill.
 */
public enum PurchaseBillStatus {
  /**
   * Bill is in draft state and can be edited/deleted by creator.
   */
  DRAFT("Draft"),

  /**
   * Bill is pending approval (for future approval workflow).
   */
  PENDING_APPROVAL("Pending Approval"),

  /**
   * Bill has been posted to the general ledger.
   */
  POSTED("Posted"),

  /**
   * Bill has been rejected (for future approval workflow).
   */
  REJECTED("Rejected"),

  /**
   * Bill has been fully paid.
   */
  PAID("Paid"),

  /**
   * Bill has been partially paid.
   */
  PARTIALLY_PAID("Partially Paid");

  private final String displayName;

  PurchaseBillStatus(String displayName) {
    this.displayName = displayName;
  }

  public String getDisplayName() {
    return displayName;
  }
}
