package com.accounting.entity;

/**
 * Enumeration representing the status of an AR receipt (customer payment).
 * AR receipts support reversal instead of cancellation for audit trail compliance.
 */
public enum ReceiptStatus {
  /**
   * Receipt is in draft state and can be edited/deleted by creator.
   */
  DRAFT("Draft"),

  /**
   * Receipt has been posted to the general ledger.
   */
  POSTED("Posted"),

  /**
   * Receipt has been reversed (creates a linked reversal voucher).
   */
  REVERSED("Reversed");

  private final String displayName;

  ReceiptStatus(String displayName) {
    this.displayName = displayName;
  }

  public String getDisplayName() {
    return displayName;
  }
}
