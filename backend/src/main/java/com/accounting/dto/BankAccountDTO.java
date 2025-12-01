package com.accounting.dto;

import com.accounting.entity.BankAccount;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * DTO for BankAccount responses. Used in API endpoints.
 */
public class BankAccountDTO {

  private Long id;
  private Long companyId;
  private String accountNumber;
  private String bankName;
  private String branch;
  private BankAccount.AccountType type;
  private BigDecimal openingBalance;
  private Boolean active;
  private String glAccountCode;
  private Boolean openingBalanceLocked;
  private LocalDate lastReconciledDate;
  private BigDecimal lastReconciledBalance;
  private Instant createdAt;
  private Instant updatedAt;

  public BankAccountDTO() {
  }

  public BankAccountDTO(
      Long id,
      Long companyId,
      String accountNumber,
      String bankName,
      String branch,
      BankAccount.AccountType type,
      BigDecimal openingBalance,
      Boolean active,
      String glAccountCode,
      Boolean openingBalanceLocked,
      LocalDate lastReconciledDate,
      BigDecimal lastReconciledBalance,
      Instant createdAt,
      Instant updatedAt) {
    this.id = id;
    this.companyId = companyId;
    this.accountNumber = accountNumber;
    this.bankName = bankName;
    this.branch = branch;
    this.type = type;
    this.openingBalance = openingBalance;
    this.active = active;
    this.glAccountCode = glAccountCode;
    this.openingBalanceLocked = openingBalanceLocked;
    this.lastReconciledDate = lastReconciledDate;
    this.lastReconciledBalance = lastReconciledBalance;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public Long getCompanyId() {
    return companyId;
  }

  public void setCompanyId(Long companyId) {
    this.companyId = companyId;
  }

  public String getAccountNumber() {
    return accountNumber;
  }

  public void setAccountNumber(String accountNumber) {
    this.accountNumber = accountNumber;
  }

  public String getBankName() {
    return bankName;
  }

  public void setBankName(String bankName) {
    this.bankName = bankName;
  }

  public String getBranch() {
    return branch;
  }

  public void setBranch(String branch) {
    this.branch = branch;
  }

  public BankAccount.AccountType getType() {
    return type;
  }

  public void setType(BankAccount.AccountType type) {
    this.type = type;
  }

  public BigDecimal getOpeningBalance() {
    return openingBalance;
  }

  public void setOpeningBalance(BigDecimal openingBalance) {
    this.openingBalance = openingBalance;
  }

  public Boolean getActive() {
    return active;
  }

  public void setActive(Boolean active) {
    this.active = active;
  }

  public String getGlAccountCode() {
    return glAccountCode;
  }

  public void setGlAccountCode(String glAccountCode) {
    this.glAccountCode = glAccountCode;
  }

  public Boolean getOpeningBalanceLocked() {
    return openingBalanceLocked;
  }

  public void setOpeningBalanceLocked(Boolean openingBalanceLocked) {
    this.openingBalanceLocked = openingBalanceLocked;
  }

  public LocalDate getLastReconciledDate() {
    return lastReconciledDate;
  }

  public void setLastReconciledDate(LocalDate lastReconciledDate) {
    this.lastReconciledDate = lastReconciledDate;
  }

  public BigDecimal getLastReconciledBalance() {
    return lastReconciledBalance;
  }

  public void setLastReconciledBalance(BigDecimal lastReconciledBalance) {
    this.lastReconciledBalance = lastReconciledBalance;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }
}
