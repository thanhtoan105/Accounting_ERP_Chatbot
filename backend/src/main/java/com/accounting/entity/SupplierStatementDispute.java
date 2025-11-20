package com.accounting.entity;

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
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * SupplierStatementDispute entity for tracking discrepancies found during supplier statement
 * reconciliation. Maintains dispute lifecycle from creation through resolution with full audit
 * trail.
 */
@Entity
@Table(name = "supplier_statement_dispute")
public class SupplierStatementDispute implements CompanyScopedEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @NotNull
  @Column(name = "company_id", nullable = false)
  private Long companyId;

  @NotNull
  @Column(name = "supplier_id", nullable = false)
  private Long supplierId;

  @Column(name = "bill_id")
  private UUID billId;

  @NotBlank
  @Column(name = "dispute_reason", nullable = false, columnDefinition = "TEXT")
  private String disputeReason;

  @NotNull
  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private DisputeStatus status = DisputeStatus.OPEN;

  @Column(name = "resolution_notes", columnDefinition = "TEXT")
  private String resolutionNotes;

  @NotNull
  @Column(name = "created_by", nullable = false)
  private Long createdBy;

  @NotNull
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "resolved_by")
  private Long resolvedBy;

  @Column(name = "resolved_at")
  private Instant resolvedAt;

  @Size(max = 100)
  @Column(name = "bill_number", length = 100)
  private String billNumber;

  @Column(name = "disputed_amount", precision = 19, scale = 2)
  private BigDecimal disputedAmount;

  @Column(name = "system_amount", precision = 19, scale = 2)
  private BigDecimal systemAmount;

  // Relationships
  @ManyToOne
  @JoinColumn(name = "company_id", insertable = false, updatable = false)
  private Company company;

  @ManyToOne
  @JoinColumn(name = "supplier_id", insertable = false, updatable = false)
  private Supplier supplier;

  @ManyToOne
  @JoinColumn(name = "bill_id", insertable = false, updatable = false)
  private PurchaseBill purchaseBill;

  @ManyToOne
  @JoinColumn(name = "created_by", insertable = false, updatable = false)
  private User createdByUser;

  @ManyToOne
  @JoinColumn(name = "resolved_by", insertable = false, updatable = false)
  private User resolvedByUser;

  @PrePersist
  public void onCreate() {
    if (createdAt == null) {
      createdAt = Instant.now();
    }
  }

  // Enum for dispute status
  public enum DisputeStatus {
    OPEN,
    IN_PROGRESS,
    RESOLVED,
    REJECTED
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

  public UUID getBillId() {
    return billId;
  }

  public void setBillId(UUID billId) {
    this.billId = billId;
  }

  public String getDisputeReason() {
    return disputeReason;
  }

  public void setDisputeReason(String disputeReason) {
    this.disputeReason = disputeReason;
  }

  public DisputeStatus getStatus() {
    return status;
  }

  public void setStatus(DisputeStatus status) {
    this.status = status;
  }

  public String getResolutionNotes() {
    return resolutionNotes;
  }

  public void setResolutionNotes(String resolutionNotes) {
    this.resolutionNotes = resolutionNotes;
  }

  public Long getCreatedBy() {
    return createdBy;
  }

  public void setCreatedBy(Long createdBy) {
    this.createdBy = createdBy;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public Long getResolvedBy() {
    return resolvedBy;
  }

  public void setResolvedBy(Long resolvedBy) {
    this.resolvedBy = resolvedBy;
  }

  public Instant getResolvedAt() {
    return resolvedAt;
  }

  public void setResolvedAt(Instant resolvedAt) {
    this.resolvedAt = resolvedAt;
  }

  public String getBillNumber() {
    return billNumber;
  }

  public void setBillNumber(String billNumber) {
    this.billNumber = billNumber;
  }

  public BigDecimal getDisputedAmount() {
    return disputedAmount;
  }

  public void setDisputedAmount(BigDecimal disputedAmount) {
    this.disputedAmount = disputedAmount;
  }

  public BigDecimal getSystemAmount() {
    return systemAmount;
  }

  public void setSystemAmount(BigDecimal systemAmount) {
    this.systemAmount = systemAmount;
  }

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

  public PurchaseBill getPurchaseBill() {
    return purchaseBill;
  }

  public void setPurchaseBill(PurchaseBill purchaseBill) {
    this.purchaseBill = purchaseBill;
  }

  public User getCreatedByUser() {
    return createdByUser;
  }

  public void setCreatedByUser(User createdByUser) {
    this.createdByUser = createdByUser;
  }

  public User getResolvedByUser() {
    return resolvedByUser;
  }

  public void setResolvedByUser(User resolvedByUser) {
    this.resolvedByUser = resolvedByUser;
  }

  /**
   * Resolves the dispute with resolution notes and resolved by user.
   */
  public void resolve(Long resolvedByUserId, String notes) {
    this.status = DisputeStatus.RESOLVED;
    this.resolvedBy = resolvedByUserId;
    this.resolvedAt = Instant.now();
    this.resolutionNotes = notes;
  }

  /**
   * Rejects the dispute with resolution notes and resolved by user.
   */
  public void reject(Long resolvedByUserId, String notes) {
    this.status = DisputeStatus.REJECTED;
    this.resolvedBy = resolvedByUserId;
    this.resolvedAt = Instant.now();
    this.resolutionNotes = notes;
  }

  /**
   * Checks if the dispute is resolved or rejected.
   */
  public boolean isClosed() {
    return status == DisputeStatus.RESOLVED || status == DisputeStatus.REJECTED;
  }
}

