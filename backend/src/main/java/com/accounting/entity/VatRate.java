package com.accounting.entity;

import java.math.BigDecimal;

/**
 * Enumeration representing VAT rates for purchase bills.
 */
public enum VatRate {
  /**
   * 0% VAT rate.
   */
  ZERO("0%", BigDecimal.ZERO),

  /**
   * 5% VAT rate.
   */
  FIVE("5%", new BigDecimal("0.05")),

  /**
   * 10% VAT rate.
   */
  TEN("10%", new BigDecimal("0.10")),

  /**
   * VAT exempt.
   */
  EXEMPT("Exempt", BigDecimal.ZERO);

  private final String displayName;
  private final BigDecimal rate;

  VatRate(String displayName, BigDecimal rate) {
    this.displayName = displayName;
    this.rate = rate;
  }

  public String getDisplayName() {
    return displayName;
  }

  public BigDecimal getRate() {
    return rate;
  }
}

