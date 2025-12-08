package com.accounting.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * DTO representing the complete Trial Balance report response.
 */
public class TrialBalanceResponseDTO {

  private AccountingPeriodDTO period;
  private String companyName;
  private Instant generatedAt;
  private List<TrialBalanceDTO> accounts;
  private BigDecimal totalOpeningDebit = BigDecimal.ZERO;
  private BigDecimal totalOpeningCredit = BigDecimal.ZERO;
  private BigDecimal totalPeriodDebit = BigDecimal.ZERO;
  private BigDecimal totalPeriodCredit = BigDecimal.ZERO;
  private BigDecimal totalClosingDebit = BigDecimal.ZERO;
  private BigDecimal totalClosingCredit = BigDecimal.ZERO;
  private boolean isBalanced = true;

  public TrialBalanceResponseDTO() {}

  public TrialBalanceResponseDTO(
      AccountingPeriodDTO period,
      String companyName,
      Instant generatedAt,
      List<TrialBalanceDTO> accounts,
      BigDecimal totalOpeningDebit,
      BigDecimal totalOpeningCredit,
      BigDecimal totalPeriodDebit,
      BigDecimal totalPeriodCredit,
      BigDecimal totalClosingDebit,
      BigDecimal totalClosingCredit) {
    this.period = period;
    this.companyName = companyName;
    this.generatedAt = generatedAt;
    this.accounts = accounts;
    this.totalOpeningDebit = totalOpeningDebit != null ? totalOpeningDebit : BigDecimal.ZERO;
    this.totalOpeningCredit = totalOpeningCredit != null ? totalOpeningCredit : BigDecimal.ZERO;
    this.totalPeriodDebit = totalPeriodDebit != null ? totalPeriodDebit : BigDecimal.ZERO;
    this.totalPeriodCredit = totalPeriodCredit != null ? totalPeriodCredit : BigDecimal.ZERO;
    this.totalClosingDebit = totalClosingDebit != null ? totalClosingDebit : BigDecimal.ZERO;
    this.totalClosingCredit = totalClosingCredit != null ? totalClosingCredit : BigDecimal.ZERO;
  }

  public AccountingPeriodDTO getPeriod() {
    return period;
  }

  public void setPeriod(AccountingPeriodDTO period) {
    this.period = period;
  }

  public String getCompanyName() {
    return companyName;
  }

  public void setCompanyName(String companyName) {
    this.companyName = companyName;
  }

  public Instant getGeneratedAt() {
    return generatedAt;
  }

  public void setGeneratedAt(Instant generatedAt) {
    this.generatedAt = generatedAt;
  }

  public List<TrialBalanceDTO> getAccounts() {
    return accounts;
  }

  public void setAccounts(List<TrialBalanceDTO> accounts) {
    this.accounts = accounts;
  }

  public BigDecimal getTotalOpeningDebit() {
    return totalOpeningDebit;
  }

  public void setTotalOpeningDebit(BigDecimal totalOpeningDebit) {
    this.totalOpeningDebit = totalOpeningDebit != null ? totalOpeningDebit : BigDecimal.ZERO;
  }

  public BigDecimal getTotalOpeningCredit() {
    return totalOpeningCredit;
  }

  public void setTotalOpeningCredit(BigDecimal totalOpeningCredit) {
    this.totalOpeningCredit = totalOpeningCredit != null ? totalOpeningCredit : BigDecimal.ZERO;
  }

  public BigDecimal getTotalPeriodDebit() {
    return totalPeriodDebit;
  }

  public void setTotalPeriodDebit(BigDecimal totalPeriodDebit) {
    this.totalPeriodDebit = totalPeriodDebit != null ? totalPeriodDebit : BigDecimal.ZERO;
  }

  public BigDecimal getTotalPeriodCredit() {
    return totalPeriodCredit;
  }

  public void setTotalPeriodCredit(BigDecimal totalPeriodCredit) {
    this.totalPeriodCredit = totalPeriodCredit != null ? totalPeriodCredit : BigDecimal.ZERO;
  }

  public BigDecimal getTotalClosingDebit() {
    return totalClosingDebit;
  }

  public void setTotalClosingDebit(BigDecimal totalClosingDebit) {
    this.totalClosingDebit = totalClosingDebit != null ? totalClosingDebit : BigDecimal.ZERO;
  }

  public BigDecimal getTotalClosingCredit() {
    return totalClosingCredit;
  }

  public void setTotalClosingCredit(BigDecimal totalClosingCredit) {
    this.totalClosingCredit = totalClosingCredit != null ? totalClosingCredit : BigDecimal.ZERO;
  }

  public boolean isBalanced() {
    return isBalanced;
  }

  public void setBalanced(boolean isBalanced) {
    this.isBalanced = isBalanced;
  }
}
