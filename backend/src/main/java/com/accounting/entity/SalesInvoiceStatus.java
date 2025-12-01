package com.accounting.entity;

/**
 * Enumeration representing the status of a sales invoice.
 */
public enum SalesInvoiceStatus {
  /**
   * Invoice is in draft state and can be edited/deleted by creator.
   */
  DRAFT("Draft"),

  /**
   * Invoice is pending approval (for future approval workflow).
   */
  PENDING_APPROVAL("Pending Approval"),

  /**
   * Invoice has been posted to the general ledger.
   */
  POSTED("Posted"),

  /**
   * Invoice has been rejected (for future approval workflow).
   */
  REJECTED("Rejected"),

  /**
   * Invoice has been fully paid.
   */
  PAID("Paid"),

  /**
   * Invoice has been partially paid.
   */
  PARTIALLY_PAID("Partially Paid");

  private final String displayName;

  SalesInvoiceStatus(String displayName) {
    this.displayName = displayName;
  }

  public String getDisplayName() {
    return displayName;
  }
}
