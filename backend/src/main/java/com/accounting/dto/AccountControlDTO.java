package com.accounting.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO for AccountControl entity.
 * Represents company-specific account control configuration for required dimensions.
 */
public class AccountControlDTO {

  private UUID id;
  private Long accountId;
  private String accountCode;
  private String accountName;
  private Long companyId;
  private Boolean requiresCustomer;
  private Boolean requiresSupplier;
  private Boolean requiresCostCenter;
  private Boolean requiresItem;
  private Instant createdAt;
  private Instant updatedAt;

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
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

  public Long getCompanyId() {
    return companyId;
  }

  public void setCompanyId(Long companyId) {
    this.companyId = companyId;
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

  public Boolean getRequiresItem() {
    return requiresItem;
  }

  public void setRequiresItem(Boolean requiresItem) {
    this.requiresItem = requiresItem;
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
