package com.accounting.dto.report;

import java.math.BigDecimal;

/**
 * DTO representing an account's contribution to a report line.
 * Used for drill-down from report line to contributing GL accounts.
 */
public class AccountContributionDTO {

  private Long accountId;
  private String accountCode;
  private String accountName;
  private BigDecimal debitAmount;
  private BigDecimal creditAmount;
  private BigDecimal netAmount;         // Based on account normal balance direction
  private BigDecimal contributionAmount; // Amount contributing to the line (after sign adjustment)
  private String normalBalance;         // 'DEBIT' or 'CREDIT'
  private Integer transactionCount;     // Number of voucher lines

  public AccountContributionDTO() {
    this.debitAmount = BigDecimal.ZERO;
    this.creditAmount = BigDecimal.ZERO;
    this.netAmount = BigDecimal.ZERO;
    this.contributionAmount = BigDecimal.ZERO;
    this.transactionCount = 0;
  }

  // Getters and Setters

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

  public BigDecimal getDebitAmount() {
    return debitAmount;
  }

  public void setDebitAmount(BigDecimal debitAmount) {
    this.debitAmount = debitAmount != null ? debitAmount : BigDecimal.ZERO;
  }

  public BigDecimal getCreditAmount() {
    return creditAmount;
  }

  public void setCreditAmount(BigDecimal creditAmount) {
    this.creditAmount = creditAmount != null ? creditAmount : BigDecimal.ZERO;
  }

  public BigDecimal getNetAmount() {
    return netAmount;
  }

  public void setNetAmount(BigDecimal netAmount) {
    this.netAmount = netAmount != null ? netAmount : BigDecimal.ZERO;
  }

  public BigDecimal getContributionAmount() {
    return contributionAmount;
  }

  public void setContributionAmount(BigDecimal contributionAmount) {
    this.contributionAmount = contributionAmount != null ? contributionAmount : BigDecimal.ZERO;
  }

  public String getNormalBalance() {
    return normalBalance;
  }

  public void setNormalBalance(String normalBalance) {
    this.normalBalance = normalBalance;
  }

  public Integer getTransactionCount() {
    return transactionCount;
  }

  public void setTransactionCount(Integer transactionCount) {
    this.transactionCount = transactionCount;
  }

  /**
   * Calculate net amount based on normal balance direction.
   * For DEBIT accounts: net = debit - credit
   * For CREDIT accounts: net = credit - debit
   */
  public void calculateNetAmount() {
    if ("CREDIT".equalsIgnoreCase(normalBalance)) {
      this.netAmount = creditAmount.subtract(debitAmount);
    } else {
      this.netAmount = debitAmount.subtract(creditAmount);
    }
  }
}
