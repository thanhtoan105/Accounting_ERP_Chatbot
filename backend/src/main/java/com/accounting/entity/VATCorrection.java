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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Tracks manual VAT corrections for purchase bills.
 */
@Entity
@Table(name = "vat_corrections")
public class VATCorrection implements CompanyScopedEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @NotNull
  @Column(name = "company_id", nullable = false)
  private Long companyId;

  @NotNull
  @Column(name = "purchase_bill_id", nullable = false)
  private UUID purchaseBillId;

  @Column(name = "purchase_bill_line_id")
  private UUID purchaseBillLineId;

  @NotNull
  @Column(name = "old_vat_amount", nullable = false, precision = 19, scale = 2)
  private BigDecimal oldVatAmount;

  @NotNull
  @Column(name = "new_vat_amount", nullable = false, precision = 19, scale = 2)
  private BigDecimal newVatAmount;

  @NotBlank
  @Column(name = "reason", nullable = false, length = 500)
  private String reason;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private Status status = Status.PENDING;

  @NotNull
  @Column(name = "corrected_by_id", nullable = false)
  private Long correctedById;

  @NotNull
  @Column(name = "corrected_at", nullable = false)
  private Instant correctedAt;

  @Column(name = "approved_by_id")
  private Long approvedById;

  @Column(name = "approved_at")
  private Instant approvedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @ManyToOne
  @JoinColumn(name = "purchase_bill_id", insertable = false, updatable = false)
  private PurchaseBill purchaseBill;

  @ManyToOne
  @JoinColumn(name = "purchase_bill_line_id", insertable = false, updatable = false)
  private PurchaseBillLine purchaseBillLine;

  @PrePersist
  protected void onCreate() {
    Instant now = Instant.now();
    createdAt = now;
    updatedAt = now;
    if (correctedAt == null) {
      correctedAt = now;
    }
  }

  @PreUpdate
  protected void onUpdate() {
    updatedAt = Instant.now();
  }

  public enum Status {
    PENDING,
    APPROVED,
    REJECTED
  }

  @Override
  public Long getCompanyId() {
    return companyId;
  }

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public void setCompanyId(Long companyId) {
    this.companyId = companyId;
  }

  public UUID getPurchaseBillId() {
    return purchaseBillId;
  }

  public void setPurchaseBillId(UUID purchaseBillId) {
    this.purchaseBillId = purchaseBillId;
  }

  public UUID getPurchaseBillLineId() {
    return purchaseBillLineId;
  }

  public void setPurchaseBillLineId(UUID purchaseBillLineId) {
    this.purchaseBillLineId = purchaseBillLineId;
  }

  public BigDecimal getOldVatAmount() {
    return oldVatAmount;
  }

  public void setOldVatAmount(BigDecimal oldVatAmount) {
    this.oldVatAmount = oldVatAmount;
  }

  public BigDecimal getNewVatAmount() {
    return newVatAmount;
  }

  public void setNewVatAmount(BigDecimal newVatAmount) {
    this.newVatAmount = newVatAmount;
  }

  public String getReason() {
    return reason;
  }

  public void setReason(String reason) {
    this.reason = reason;
  }

  public Status getStatus() {
    return status;
  }

  public void setStatus(Status status) {
    this.status = status;
  }

  public Long getCorrectedById() {
    return correctedById;
  }

  public void setCorrectedById(Long correctedById) {
    this.correctedById = correctedById;
  }

  public Instant getCorrectedAt() {
    return correctedAt;
  }

  public void setCorrectedAt(Instant correctedAt) {
    this.correctedAt = correctedAt;
  }

  public Long getApprovedById() {
    return approvedById;
  }

  public void setApprovedById(Long approvedById) {
    this.approvedById = approvedById;
  }

  public Instant getApprovedAt() {
    return approvedAt;
  }

  public void setApprovedAt(Instant approvedAt) {
    this.approvedAt = approvedAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public PurchaseBill getPurchaseBill() {
    return purchaseBill;
  }

  public PurchaseBillLine getPurchaseBillLine() {
    return purchaseBillLine;
  }
}

