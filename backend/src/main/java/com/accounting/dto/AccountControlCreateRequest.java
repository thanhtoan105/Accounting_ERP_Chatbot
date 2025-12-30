package com.accounting.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for creating or updating AccountControl.
 */
public class AccountControlCreateRequest {

  @NotNull(message = "Account ID is required")
  private Long accountId;

  private Boolean requiresCustomer = false;
  private Boolean requiresSupplier = false;
  private Boolean requiresItem = false;

  public Long getAccountId() {
    return accountId;
  }

  public void setAccountId(Long accountId) {
    this.accountId = accountId;
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

  public Boolean getRequiresItem() {
    return requiresItem;
  }

  public void setRequiresItem(Boolean requiresItem) {
    this.requiresItem = requiresItem;
  }
}
