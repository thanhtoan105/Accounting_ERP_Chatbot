package com.accounting.entity;

import com.accounting.repository.CompanyScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Default Account entity representing preset accounts for voucher types.
 * Each company can define their own default accounts.
 */
@Entity
@Table(name = "default_accounts")
public class DefaultAccount implements CompanyScopedEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @NotNull
  @Column(name = "company_id", nullable = false)
  private Long companyId;

  @NotBlank
  @Size(max = 50)
  @Column(name = "voucher_type", nullable = false, length = 50)
  private String voucherType; // Cash Payment, Bank Payment, Cash Receipt, Bank Receipt, Other Business Voucher

  @NotBlank
  @Size(max = 255)
  @Column(name = "entry_name", nullable = false, length = 255)
  private String entryName;

  @NotBlank
  @Column(name = "status", nullable = false, length = 20)
  private String status = "ACTIVE"; // ACTIVE or INACTIVE

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  // Relationships
  @OneToMany(mappedBy = "defaultAccount", cascade = jakarta.persistence.CascadeType.ALL, orphanRemoval = true)
  private List<DefaultAccountEntry> entries = new ArrayList<>();

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

  public String getVoucherType() {
    return voucherType;
  }

  public void setVoucherType(String voucherType) {
    this.voucherType = voucherType;
  }

  public String getEntryName() {
    return entryName;
  }

  public void setEntryName(String entryName) {
    this.entryName = entryName;
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

  public List<DefaultAccountEntry> getEntries() {
    return entries;
  }

  public void setEntries(List<DefaultAccountEntry> entries) {
    this.entries = entries;
  }

  public Company getCompany() {
    return company;
  }

  public void setCompany(Company company) {
    this.company = company;
  }
}

