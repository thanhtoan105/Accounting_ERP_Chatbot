package com.accounting.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import com.accounting.repository.CompanyScopedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * BankAccount entity for managing cash and bank accounts.
 * Represents a bank account with company scoping and validation.
 */
@Entity
@Table(name = "bank_accounts")
public class BankAccount implements CompanyScopedEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "company_id", nullable = false)
  private Long companyId;

  @NotBlank
  @Size(max = 50)
  @Column(name = "account_number", nullable = false, length = 50)
  private String accountNumber;

  @NotBlank
  @Size(max = 255)
  @Column(name = "bank_name", nullable = false, length = 255)
  private String bankName;

  @Size(max = 255)
  @Column(name = "branch", length = 255)
  private String branch;

  @NotNull
  @Enumerated(EnumType.STRING)
  @Column(name = "type", nullable = false, length = 10)
  private AccountType type;

  @NotNull
  @DecimalMin(value = "0.0", message = "Opening balance must be non-negative")
  @Column(name = "opening_balance", nullable = false, precision = 19, scale = 2)
  private BigDecimal openingBalance;

  @Column(name = "active", nullable = false)
  private Boolean active = true;

  @Size(max = 20)
  @Column(name = "gl_account_code", length = 20)
  private String glAccountCode;

  @Column(name = "opening_balance_locked", nullable = false)
  private Boolean openingBalanceLocked = false;

  @Column(name = "last_reconciled_date")
  private LocalDate lastReconciledDate;

  @Column(name = "last_reconciled_balance", precision = 19, scale = 2)
  private BigDecimal lastReconciledBalance;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  protected void onCreate() {
    createdAt = Instant.now();
    updatedAt = Instant.now();
  }

  @PreUpdate
  protected void onUpdate() {
    updatedAt = Instant.now();
  }

  public enum AccountType {
    CASH,
    BANK
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  @Override
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

  public AccountType getType() {
    return type;
  }

  public void setType(AccountType type) {
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
