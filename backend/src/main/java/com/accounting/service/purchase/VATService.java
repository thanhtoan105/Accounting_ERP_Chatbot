package com.accounting.service.purchase;

import com.accounting.entity.VatRate;
import java.math.BigDecimal;

/**
 * Service for VAT calculation and validation for purchase bills.
 * Supports TT200 compliance with VAT mapping to account 3331.
 */
public interface VATService {

  /**
   * Calculate VAT amount from base amount and VAT rate.
   * Supports 0%, 5%, 10%, and EXEMPT rates.
   *
   * @param amount base amount (before VAT)
   * @param rate VAT rate (ZERO, FIVE, TEN, or EXEMPT)
   * @return calculated VAT amount (rounded to 2 decimal places)
   */
  BigDecimal calculateVAT(BigDecimal amount, VatRate rate);

  /**
   * Validate that header VAT amount matches the sum of line item VAT amounts.
   * Uses tolerance of 1,000₫ for rounding differences.
   *
   * @param headerVAT header VAT amount
   * @param lineVATSum sum of line item VAT amounts
   * @return true if VAT sum matches (within tolerance), false otherwise
   */
  boolean validateVATSum(BigDecimal headerVAT, BigDecimal lineVATSum);

  /**
   * Get the difference between header VAT and line VAT sum.
   * Returns absolute difference for error reporting.
   *
   * @param headerVAT header VAT amount
   * @param lineVATSum sum of line item VAT amounts
   * @return absolute difference
   */
  BigDecimal getVATSumDifference(BigDecimal headerVAT, BigDecimal lineVATSum);

  /**
   * Map VAT amount to General Ledger account 3331 (VAT payable account).
   * This is required for TT200 compliance (Vietnamese accounting standard).
   *
   * @param vatAmount VAT amount to map
   * @return GL account code (3331) for VAT payable
   */
  String mapToGL(BigDecimal vatAmount);

  /**
   * Get VAT tolerance for validation (1,000₫).
   *
   * @return VAT tolerance amount
   */
  BigDecimal getVATTolerance();
}

