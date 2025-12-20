package com.accounting.entity;

/**
 * Enumeration representing the payment method for an AP payment.
 */
public enum PaymentMethod {
  /**
   * Cash payment.
   */
  CASH("Cash"),

  /**
   * Bank transfer payment.
   */
  BANK_TRANSFER("Bank Transfer"),

  /**
   * Check payment.
   */
  CHECK("Check"),

  /**
   * Other payment method.
   */
  OTHER("Other");

  private final String displayName;

  PaymentMethod(String displayName) {
    this.displayName = displayName;
  }

  public String getDisplayName() {
    return displayName;
  }
}
