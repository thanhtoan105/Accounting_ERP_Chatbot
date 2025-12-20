package com.accounting.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO for JournalEntry entity.
 * Journal entries are immutable after creation and represent general ledger entries.
 */
public class JournalEntryDTO {

  private UUID id;
  private UUID voucherId;
  private Long accountId;
  private String accountCode;
  private String accountName;
  private UUID periodId;
  private BigDecimal debitAmount;
  private BigDecimal creditAmount;
  private Long customerId;
  private String customerName;
  private Long supplierId;
  private String supplierName;
  private Long costCenterId;
  private String costCenterName;
  private Long companyId;
  private Instant postedAt;
  private Instant createdAt;

  public JournalEntryDTO() {}

  public JournalEntryDTO(
      UUID id,
      UUID voucherId,
      Long accountId,
      String accountCode,
      String accountName,
      UUID periodId,
      BigDecimal debitAmount,
      BigDecimal creditAmount,
      Long customerId,
      String customerName,
      Long supplierId,
      String supplierName,
      Long costCenterId,
      String costCenterName,
      Long companyId,
      Instant postedAt,
      Instant createdAt) {
    this.id = id;
    this.voucherId = voucherId;
    this.accountId = accountId;
    this.accountCode = accountCode;
    this.accountName = accountName;
    this.periodId = periodId;
    this.debitAmount = debitAmount;
    this.creditAmount = creditAmount;
    this.customerId = customerId;
    this.customerName = customerName;
    this.supplierId = supplierId;
    this.supplierName = supplierName;
    this.costCenterId = costCenterId;
    this.costCenterName = costCenterName;
    this.companyId = companyId;
    this.postedAt = postedAt;
    this.createdAt = createdAt;
  }

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

  public String getCustomerName() {
    return customerName;
  }

  public void setCustomerName(String customerName) {
    this.customerName = customerName;
  }

  public Long getSupplierId() {
    return supplierId;
  }

  public void setSupplierId(Long supplierId) {
    this.supplierId = supplierId;
  }

  public String getSupplierName() {
    return supplierName;
  }

  public void setSupplierName(String supplierName) {
    this.supplierName = supplierName;
  }

  public Long getCostCenterId() {
    return costCenterId;
  }

  public void setCostCenterId(Long costCenterId) {
    this.costCenterId = costCenterId;
  }

  public String getCostCenterName() {
    return costCenterName;
  }

  public void setCostCenterName(String costCenterName) {
    this.costCenterName = costCenterName;
  }

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
}
