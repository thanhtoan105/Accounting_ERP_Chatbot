package com.accounting.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.accounting.repository.CompanyScopedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * PurchaseBill entity representing supplier bills in the accounts payable module.
 * Bills can be in draft, pending approval, posted, rejected, paid, or partially paid status.
 */
@Entity
@Table(name = "purchase_bills")
public class PurchaseBill implements CompanyScopedEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @NotNull
  @Column(name = "company_id", nullable = false)
  private Long companyId;

  @NotNull
  @Column(name = "supplier_id", nullable = false)
  private Long supplierId;

  @NotBlank
  @Size(max = 50)
  @Column(name = "bill_number", nullable = false, length = 50)
  private String billNumber;

  @NotNull
  @Column(name = "bill_date", nullable = false)
  private LocalDate billDate;

  @NotNull
  @Column(name = "due_date", nullable = false)
  private LocalDate dueDate;

  @NotBlank
  @Size(max = 100)
  @Column(name = "reference", nullable = false, length = 100)
  private String reference;

  @Size(max = 500)
  @Column(name = "description", length = 500)
  private String description;

  @NotNull
  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private PurchaseBillStatus status = PurchaseBillStatus.DRAFT;

  @NotNull
  @Positive
  @Column(name = "total_amount", nullable = false, precision = 19, scale = 2)
  private BigDecimal totalAmount = BigDecimal.ZERO;

  @NotNull
  @Column(name = "vat_amount", nullable = false, precision = 19, scale = 2)
  private BigDecimal vatAmount = BigDecimal.ZERO;

  @NotNull
  @Column(name = "created_by_id", nullable = false)
  private Long createdById;

  @Column(name = "approved_by_id")
  private Long approvedById;

  @Column(name = "posted_voucher_id")
  private UUID postedVoucherId;

  @NotNull
  @Column(name = "is_sensitive", nullable = false)
  private Boolean isSensitive = false;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  // Relationships
  @ManyToOne
  @JoinColumn(name = "company_id", insertable = false, updatable = false)
  private Company company;

  @ManyToOne
  @JoinColumn(name = "supplier_id", insertable = false, updatable = false)
  private Supplier supplier;

  @ManyToOne
  @JoinColumn(name = "created_by_id", insertable = false, updatable = false)
  private User createdBy;

  @ManyToOne
  @JoinColumn(name = "approved_by_id", insertable = false, updatable = false)
  private User approvedBy;

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
  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  @Override
  public Long getCompanyId() {
    return companyId;
  }

  public void setCompanyId(Long companyId) {
    this.companyId = companyId;
  }

  public Long getSupplierId() {
    return supplierId;
  }

  public void setSupplierId(Long supplierId) {
    this.supplierId = supplierId;
  }

  public String getBillNumber() {
    return billNumber;
  }

  public void setBillNumber(String billNumber) {
    this.billNumber = billNumber;
  }

  public LocalDate getBillDate() {
    return billDate;
  }

  public void setBillDate(LocalDate billDate) {
    this.billDate = billDate;
  }

  public LocalDate getDueDate() {
    return dueDate;
  }

  public void setDueDate(LocalDate dueDate) {
    this.dueDate = dueDate;
  }

  public String getReference() {
    return reference;
  }

  public void setReference(String reference) {
    this.reference = reference;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public PurchaseBillStatus getStatus() {
    return status;
  }

  public void setStatus(PurchaseBillStatus status) {
    this.status = status;
  }

  public BigDecimal getTotalAmount() {
    return totalAmount;
  }

  public void setTotalAmount(BigDecimal totalAmount) {
    this.totalAmount = totalAmount;
  }

  public BigDecimal getVatAmount() {
    return vatAmount;
  }

  public void setVatAmount(BigDecimal vatAmount) {
    this.vatAmount = vatAmount;
  }

  public Long getCreatedById() {
    return createdById;
  }

  public void setCreatedById(Long createdById) {
    this.createdById = createdById;
  }

  public Long getApprovedById() {
    return approvedById;
  }

  public void setApprovedById(Long approvedById) {
    this.approvedById = approvedById;
  }

  public UUID getPostedVoucherId() {
    return postedVoucherId;
  }

  public void setPostedVoucherId(UUID postedVoucherId) {
    this.postedVoucherId = postedVoucherId;
  }

  public Boolean getIsSensitive() {
    return isSensitive;
  }

  public void setIsSensitive(Boolean isSensitive) {
    this.isSensitive = isSensitive;
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
  public Company getCompany() {
    return company;
  }

  public void setCompany(Company company) {
    this.company = company;
  }

  public Supplier getSupplier() {
    return supplier;
  }

  public void setSupplier(Supplier supplier) {
    this.supplier = supplier;
  }

  public User getCreatedBy() {
    return createdBy;
  }

  public void setCreatedBy(User createdBy) {
    this.createdBy = createdBy;
  }

  public User getApprovedBy() {
    return approvedBy;
  }

  public void setApprovedBy(User approvedBy) {
    this.approvedBy = approvedBy;
  }
}
