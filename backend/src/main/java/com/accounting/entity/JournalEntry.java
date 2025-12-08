package com.accounting.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.accounting.repository.CompanyScopedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;

/**
 * JournalEntry entity representing immutable general ledger entries.
 * Journal entries are created when vouchers are posted and are used for
 * reporting (Trial Balance, Financial Statements).
 */
@Entity
@Table(name = "journal_entries")
public class JournalEntry implements CompanyScopedEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @NotNull
  @Column(name = "voucher_id", nullable = false)
  private UUID voucherId;

  @NotNull
  @Column(name = "account_id", nullable = false)
  private Long accountId;

  @Column(name = "period_id")
  private UUID periodId;

  @NotNull
  @Column(name = "debit_amount", nullable = false, precision = 19, scale = 2)
  private BigDecimal debitAmount = BigDecimal.ZERO;

  @NotNull
  @Column(name = "credit_amount", nullable = false, precision = 19, scale = 2)
  private BigDecimal creditAmount = BigDecimal.ZERO;

  @Column(name = "customer_id")
  private Long customerId;

  @Column(name = "supplier_id")
  private Long supplierId;

  @Column(name = "cost_center_id")
  private Long costCenterId;

  @NotNull
  @Column(name = "company_id", nullable = false)
  private Long companyId;

  @NotNull
  @Column(name = "posted_at", nullable = false)
  private Instant postedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  // Relationships (read-only)
  @ManyToOne
  @JoinColumn(name = "voucher_id", insertable = false, updatable = false)
  private Voucher voucher;

  @ManyToOne
  @JoinColumn(name = "account_id", insertable = false, updatable = false)
  private ChartOfAccount account;

  @ManyToOne
  @JoinColumn(name = "company_id", insertable = false, updatable = false)
  private Company company;

  // Getters and setters
  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public UUID getVoucherId() {
    return voucherId;
  }

  public void setVoucherId(UUID voucherId) {
    this.voucherId = voucherId;
  }

  public Long getAccountId() {
    return accountId;
  }

  public void setAccountId(Long accountId) {
    this.accountId = accountId;
  }

  public UUID getPeriodId() {
    return periodId;
  }

  public void setPeriodId(UUID periodId) {
    this.periodId = periodId;
  }

  public BigDecimal getDebitAmount() {
    return debitAmount;
  }

  public void setDebitAmount(BigDecimal debitAmount) {
    this.debitAmount = debitAmount;
  }

  public BigDecimal getCreditAmount() {
    return creditAmount;
  }

  public void setCreditAmount(BigDecimal creditAmount) {
    this.creditAmount = creditAmount;
  }

  public Long getCustomerId() {
    return customerId;
  }

  public void setCustomerId(Long customerId) {
    this.customerId = customerId;
  }

  public Long getSupplierId() {
    return supplierId;
  }

  public void setSupplierId(Long supplierId) {
    this.supplierId = supplierId;
  }

  public Long getCostCenterId() {
    return costCenterId;
  }

  public void setCostCenterId(Long costCenterId) {
    this.costCenterId = costCenterId;
  }

  @Override
  public Long getCompanyId() {
    return companyId;
  }

  public void setCompanyId(Long companyId) {
    this.companyId = companyId;
  }

  public Instant getPostedAt() {
    return postedAt;
  }

  public void setPostedAt(Instant postedAt) {
    this.postedAt = postedAt;
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

  // Relationship getters (read-only)
  public Voucher getVoucher() {
    return voucher;
  }

  public void setVoucher(Voucher voucher) {
    this.voucher = voucher;
  }

  public ChartOfAccount getAccount() {
    return account;
  }

  public void setAccount(ChartOfAccount account) {
    this.account = account;
  }

  public Company getCompany() {
    return company;
  }

  public void setCompany(Company company) {
    this.company = company;
  }
}
