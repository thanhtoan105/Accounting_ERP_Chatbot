package com.accounting.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class VoucherTemplateLineRequest {

  @NotNull(message = "debitAccountId is required")
  private Long debitAccountId;

  @NotNull(message = "creditAccountId is required")
  private Long creditAccountId;

  @Size(max = 500, message = "defaultDescription must be 500 characters or less")
  private String defaultDescription;

  private Boolean requiresCustomer = Boolean.FALSE;
  private Boolean requiresSupplier = Boolean.FALSE;
  private Boolean requiresCostCenter = Boolean.FALSE;
  private Boolean lockAccounts = Boolean.FALSE;

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

  public String getDefaultDescription() {
    return defaultDescription;
  }

  public void setDefaultDescription(String defaultDescription) {
    this.defaultDescription = defaultDescription;
  }

  public Boolean getRequiresCustomer() {
    return requiresCustomer;
  }

  public void setRequiresCustomer(Boolean requiresCustomer) {
    this.requiresCustomer = requiresCustomer;
  }

  public Boolean getRequiresSupplier() {
    return requiresSupplier;
  }

  public void setRequiresSupplier(Boolean requiresSupplier) {
    this.requiresSupplier = requiresSupplier;
  }

  public Boolean getRequiresCostCenter() {
    return requiresCostCenter;
  }

  public void setRequiresCostCenter(Boolean requiresCostCenter) {
    this.requiresCostCenter = requiresCostCenter;
  }

  public Boolean getLockAccounts() {
    return lockAccounts;
  }

  public void setLockAccounts(Boolean lockAccounts) {
    this.lockAccounts = lockAccounts;
  }
}

