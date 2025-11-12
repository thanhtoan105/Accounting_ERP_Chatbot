package com.accounting.dto;

import java.math.BigDecimal;

/**
 * DTO for balance tooltip data showing current and prior period balances.
 */
public class BalanceTooltipDTO {

  private BigDecimal currentBalance;
  private BigDecimal priorBalance;
  private String currentPeriod;
  private String priorPeriod;

  public BalanceTooltipDTO() {}

  public BalanceTooltipDTO(
      BigDecimal currentBalance,
      BigDecimal priorBalance,
      String currentPeriod,
      String priorPeriod) {
    this.currentBalance = currentBalance;
    this.priorBalance = priorBalance;
    this.currentPeriod = currentPeriod;
    this.priorPeriod = priorPeriod;
  }

  public BigDecimal getCurrentBalance() {
    return currentBalance;
  }

  public void setCurrentBalance(BigDecimal currentBalance) {
    this.currentBalance = currentBalance;
  }

  public BigDecimal getPriorBalance() {
    return priorBalance;
  }

  public void setPriorBalance(BigDecimal priorBalance) {
    this.priorBalance = priorBalance;
  }

  public String getCurrentPeriod() {
    return currentPeriod;
  }

  public void setCurrentPeriod(String currentPeriod) {
    this.currentPeriod = currentPeriod;
  }

  public String getPriorPeriod() {
    return priorPeriod;
  }

  public void setPriorPeriod(String priorPeriod) {
    this.priorPeriod = priorPeriod;
  }
}

