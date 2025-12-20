package com.accounting.service;

import java.math.BigDecimal;

/**
 * Service for account balance validation and calculation.
 * Handles balance checks for cash/bank accounts and overdraft detection.
 */
public interface AccountBalanceService {

  /**
   * Get current balance for a cash/bank account.
   * Calculates: opening balance + sum of journal entries (debit - credit for asset accounts).
   *
   * @param accountId bank account ID
   * @return current balance
   */
  BigDecimal getAccountBalance(Long accountId);

  /**
   * Validate if account has sufficient balance for payment.
   *
   * @param accountId bank account ID
   * @param paymentAmount payment amount
   * @return true if balance >= paymentAmount, false otherwise
   */
  boolean validateSufficientBalance(Long accountId, BigDecimal paymentAmount);

  /**
   * Check overdraft status and return result with warning/block indicator.
   * Based on CompanySettings.overdraftPolicy configuration.
   *
   * @param accountId bank account ID
   * @param paymentAmount payment amount
   * @return OverdraftResult with status (OK, WARNING, BLOCK) and message
   */
  OverdraftResult checkOverdraft(Long accountId, BigDecimal paymentAmount);

  /**
   * Result class for overdraft check.
   */
  class OverdraftResult {
    private OverdraftStatus status;
    private String message;
    private BigDecimal currentBalance;
    private BigDecimal projectedBalance;

    public OverdraftResult(
        OverdraftStatus status,
        String message,
        BigDecimal currentBalance,
        BigDecimal projectedBalance) {
      this.status = status;
      this.message = message;
      this.currentBalance = currentBalance;
      this.projectedBalance = projectedBalance;
    }

    public OverdraftStatus getStatus() {
      return status;
    }

    public String getMessage() {
      return message;
    }

    public BigDecimal getCurrentBalance() {
      return currentBalance;
    }

    public BigDecimal getProjectedBalance() {
      return projectedBalance;
    }
  }

  enum OverdraftStatus {
    OK,
    WARNING,
    BLOCK
  }
}
