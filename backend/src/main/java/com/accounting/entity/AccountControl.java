package com.accounting.entity;

import com.accounting.repository.CompanyScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

/**
 * Account Control entity for company-specific required dimension configuration.
 * Defines which dimensions (customer, supplier, cost center, item) are required
 * for specific accounts per company.
 */
@Entity
@Table(name = "account_controls")
public class AccountControl implements CompanyScopedEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @NotNull
  @Column(name = "account_id", nullable = false)
  private Long accountId;

  @ManyToOne
  @JoinColumn(name = "account_id", insertable = false, updatable = false)
  private ChartOfAccount account;

  @NotNull
  @Column(name = "company_id", nullable = false)
  private Long companyId;

  @NotNull
  @Column(name = "requires_customer", nullable = false)
  private Boolean requiresCustomer = false;

  @NotNull
  @Column(name = "requires_supplier", nullable = false)
  private Boolean requiresSupplier = false;

  @NotNull
  @Column(name = "requires_cost_center", nullable = false)
  private Boolean requiresCostCenter = false;

  @NotNull
  @Column(name = "requires_item", nullable = false)
  private Boolean requiresItem = false;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PreUpdate
  public void preUpdate() {
    this.updatedAt = Instant.now();
  }

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

  public ChartOfAccount getAccount() {
    return account;
  }

  public void setAccount(ChartOfAccount account) {
    this.account = account;
  }

  @Override
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

