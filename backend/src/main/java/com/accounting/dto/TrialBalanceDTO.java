package com.accounting.dto;

import java.math.BigDecimal;

/**
 * DTO representing a single account line in the Trial Balance report.
 */
public class TrialBalanceDTO {

  private Long accountId;
  private String accountCode;
  private String accountName;
  private BigDecimal openingDebit = BigDecimal.ZERO;
  private BigDecimal openingCredit = BigDecimal.ZERO;
  private BigDecimal periodDebit = BigDecimal.ZERO;
  private BigDecimal periodCredit = BigDecimal.ZERO;
  private BigDecimal closingDebit = BigDecimal.ZERO;
  private BigDecimal closingCredit = BigDecimal.ZERO;

  public TrialBalanceDTO() {}

  public TrialBalanceDTO(
      Long accountId,
      String accountCode,
      String accountName,
      BigDecimal openingDebit,
      BigDecimal openingCredit,
      BigDecimal periodDebit,
      BigDecimal periodCredit,
      BigDecimal closingDebit,
      BigDecimal closingCredit) {
    this.accountId = accountId;
    this.accountCode = accountCode;
    this.accountName = accountName;
    this.openingDebit = openingDebit != null ? openingDebit : BigDecimal.ZERO;
    this.openingCredit = openingCredit != null ? openingCredit : BigDecimal.ZERO;
    this.periodDebit = periodDebit != null ? periodDebit : BigDecimal.ZERO;
    this.periodCredit = periodCredit != null ? periodCredit : BigDecimal.ZERO;
    this.closingDebit = closingDebit != null ? closingDebit : BigDecimal.ZERO;
    this.closingCredit = closingCredit != null ? closingCredit : BigDecimal.ZERO;
  }

  public Long getAccountId() {
    return accountId;
  }

  public void setAccountId(Long accountId) {
    this.accountId = accountId;
  }

  public String getAccountCode() {
    return accountCode;
  }

  public void setAccountCode(String accountCode) {
    this.accountCode = accountCode;
  }

  public String getAccountName() {
    return accountName;
  }

  public void setAccountName(String accountName) {
    this.accountName = accountName;
  }

  public BigDecimal getOpeningDebit() {
    return openingDebit;
  }

  public void setOpeningDebit(BigDecimal openingDebit) {
    this.openingDebit = openingDebit != null ? openingDebit : BigDecimal.ZERO;
  }

  public BigDecimal getOpeningCredit() {
    return openingCredit;
  }

  public void setOpeningCredit(BigDecimal openingCredit) {
    this.openingCredit = openingCredit != null ? openingCredit : BigDecimal.ZERO;
  }

  public BigDecimal getPeriodDebit() {
    return periodDebit;
  }

  public void setPeriodDebit(BigDecimal periodDebit) {
    this.periodDebit = periodDebit != null ? periodDebit : BigDecimal.ZERO;
  }

  public BigDecimal getPeriodCredit() {
    return periodCredit;
  }

  public void setPeriodCredit(BigDecimal periodCredit) {
    this.periodCredit = periodCredit != null ? periodCredit : BigDecimal.ZERO;
  }

  public BigDecimal getClosingDebit() {
    return closingDebit;
  }

  public void setClosingDebit(BigDecimal closingDebit) {
    this.closingDebit = closingDebit != null ? closingDebit : BigDecimal.ZERO;
  }

  public BigDecimal getClosingCredit() {
    return closingCredit;
  }

  public void setClosingCredit(BigDecimal closingCredit) {
    this.closingCredit = closingCredit != null ? closingCredit : BigDecimal.ZERO;
  }
}
