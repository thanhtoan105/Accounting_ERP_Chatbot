package com.accounting.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Request payload representing a single voucher entry line (one debit + one credit account).
 * Each entry line will be transformed into two voucher lines (debit/credit) by the service layer.
 */
public class VoucherEntryLineRequest {

  @NotNull(message = "Debit account is required")
  private Long debitAccountId;

  @NotNull(message = "Credit account is required")
  private Long creditAccountId;

  @NotNull(message = "Amount is required")
  @DecimalMin(value = "0.00", inclusive = false, message = "Amount must be greater than 0")
  private BigDecimal amount;

  @Size(max = 500, message = "Line description must not exceed 500 characters")
  private String description;

  private Long customerId;
  private Long supplierId;
  private Long costCenterId;
  private Long itemId;

  private UUID templateLineId;
  private Boolean lockAccounts;

  public Long getDebitAccountId() {
    return debitAccountId;
  }

  public void setDebitAccountId(Long debitAccountId) {
    this.debitAccountId = debitAccountId;
  }

  public Long getCreditAccountId() {
    return creditAccountId;
  }

  public void setCreditAccountId(Long creditAccountId) {
    this.creditAccountId = creditAccountId;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public void setAmount(BigDecimal amount) {
    this.amount = amount;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
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

  public Long getItemId() {
    return itemId;
  }

  public void setItemId(Long itemId) {
    this.itemId = itemId;
  }

  public UUID getTemplateLineId() {
    return templateLineId;
  }

  public void setTemplateLineId(UUID templateLineId) {
    this.templateLineId = templateLineId;
  }

  public Boolean getLockAccounts() {
    return lockAccounts;
  }

  public void setLockAccounts(Boolean lockAccounts) {
    this.lockAccounts = lockAccounts;
  }
}


