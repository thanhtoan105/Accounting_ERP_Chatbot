package com.accounting.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO for balance tooltip data showing current balance and key dates.
 * Used in account picker tooltip per AC6.1-06.
 */
public class BalanceTooltipDTO {

  private BigDecimal currentBalance;
  private LocalDate lastTxDate;
  private LocalDate lastReconciledDate;

  public BalanceTooltipDTO() {
  }

  public BalanceTooltipDTO(
      BigDecimal currentBalance,
      LocalDate lastTxDate,
      LocalDate lastReconciledDate) {
    this.currentBalance = currentBalance;
    this.lastTxDate = lastTxDate;
    this.lastReconciledDate = lastReconciledDate;
  }

  /**
   * Legacy constructor for backward compatibility.
   * 
   * @deprecated Use new constructor with LocalDate parameters
   */
  @Deprecated
  public BalanceTooltipDTO(
      BigDecimal currentBalance,
      BigDecimal priorBalance,
      String currentPeriod,
      String priorPeriod) {
    this.currentBalance = currentBalance;
    this.lastTxDate = null;
    this.lastReconciledDate = null;
  }

  public BigDecimal getCurrentBalance() {
    return currentBalance;
  }

  public void setCurrentBalance(BigDecimal currentBalance) {
    this.currentBalance = currentBalance;
  }

  public LocalDate getLastTxDate() {
    return lastTxDate;
  }

  public void setLastTxDate(LocalDate lastTxDate) {
    this.lastTxDate = lastTxDate;
  }

  public LocalDate getLastReconciledDate() {
    return lastReconciledDate;
  }

  public void setLastReconciledDate(LocalDate lastReconciledDate) {
    this.lastReconciledDate = lastReconciledDate;
  }
}
