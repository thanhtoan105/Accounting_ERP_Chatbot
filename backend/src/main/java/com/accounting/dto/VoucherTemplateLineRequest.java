package com.accounting.dto;

import jakarta.validation.constraints.Size;

public class VoucherTemplateLineRequest {

  // At least one of debitAccountId or creditAccountId must be provided (validated
  // in service)
  private Long debitAccountId;

  private Long creditAccountId;

  @Size(max = 500, message = "defaultDescription must be 500 characters or less")
  private String defaultDescription;

  private Boolean requiresCustomer = Boolean.FALSE;
  private Boolean requiresSupplier = Boolean.FALSE;
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

  public Boolean getLockAccounts() {
    return lockAccounts;
  }

  public void setLockAccounts(Boolean lockAccounts) {
    this.lockAccounts = lockAccounts;
  }
}
