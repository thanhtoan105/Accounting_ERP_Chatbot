package com.accounting.entity;

import com.accounting.repository.CompanyScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/**
 * Voucher Type entity representing predefined voucher types with associated debit/credit accounts.
 * Each company can define their own voucher types.
 */
@Entity
@Table(name = "voucher_types")
public class VoucherType implements CompanyScopedEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @NotNull
  @Column(name = "company_id", nullable = false)
  private Long companyId;

  @NotBlank
  @Size(max = 50)
  @Column(name = "type_code", nullable = false, length = 50)
  private String typeCode;

  @NotBlank
  @Size(max = 255)
  @Column(name = "type_name", nullable = false, length = 255)
  private String typeName;

  @Column(name = "debit_account_id")
  private Long debitAccountId;

  @Column(name = "credit_account_id")
  private Long creditAccountId;

  @Column(name = "description", length = 1000)
  private String description;

  @NotBlank
  @Column(name = "status", nullable = false, length = 20)
  private String status = "ACTIVE"; // ACTIVE or INACTIVE

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  // Relationships
  @ManyToOne
  @JoinColumn(name = "debit_account_id", insertable = false, updatable = false)
  private ChartOfAccount debitAccount;

  @ManyToOne
  @JoinColumn(name = "credit_account_id", insertable = false, updatable = false)
  private ChartOfAccount creditAccount;

  @ManyToOne
  @JoinColumn(name = "company_id", insertable = false, updatable = false)
  private Company company;

  @PrePersist
  protected void onCreate() {
    createdAt = Instant.now();
    updatedAt = Instant.now();
  }

  @PreUpdate
  protected void onUpdate() {
    updatedAt = Instant.now();
  }

  // Getters and setters
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

  public String getTypeCode() {
    return typeCode;
  }

  public void setTypeCode(String typeCode) {
    this.typeCode = typeCode;
  }

  public String getTypeName() {
    return typeName;
  }

  public void setTypeName(String typeName) {
    this.typeName = typeName;
  }

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

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
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
  public ChartOfAccount getDebitAccount() {
    return debitAccount;
  }

  public void setDebitAccount(ChartOfAccount debitAccount) {
    this.debitAccount = debitAccount;
  }

  public ChartOfAccount getCreditAccount() {
    return creditAccount;
  }

  public void setCreditAccount(ChartOfAccount creditAccount) {
    this.creditAccount = creditAccount;
  }

  public Company getCompany() {
    return company;
  }

  public void setCompany(Company company) {
    this.company = company;
  }
}


