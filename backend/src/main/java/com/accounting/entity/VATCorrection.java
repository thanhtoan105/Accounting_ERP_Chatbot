package com.accounting.entity;

import com.accounting.repository.CompanyScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
  @Column(name = "document_type", nullable = false, length = 50)
  private String documentType;

  @NotNull
  @Column(name = "document_id", nullable = false)
  private UUID documentId;

  @Column(name = "line_item_id")
  private UUID lineItemId;

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

  // Note: Relations removed - using polymorphic document_type/document_id pattern
  // Lookup purchaseBill/salesInvoice by documentType + documentId at service
  // layer

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

  public String getDocumentType() {
    return documentType;
  }

  public void setDocumentType(String documentType) {
    this.documentType = documentType;
  }

  public UUID getDocumentId() {
    return documentId;
  }

  public void setDocumentId(UUID documentId) {
    this.documentId = documentId;
  }

  public UUID getLineItemId() {
    return lineItemId;
  }

  public void setLineItemId(UUID lineItemId) {
    this.lineItemId = lineItemId;
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

  // Backward-compatible convenience methods for purchase bill corrections
  // Maps to polymorphic document_type/document_id/line_item_id pattern

  /**
   * Sets purchase bill ID and document type.
   * Convenience method for backward compatibility.
   */
  public void setPurchaseBillId(UUID purchaseBillId) {
    this.documentType = "PURCHASE_BILL";
    this.documentId = purchaseBillId;
  }

  /**
   * Gets purchase bill ID (alias for documentId when documentType is
   * PURCHASE_BILL).
   * Convenience method for backward compatibility.
   */
  public UUID getPurchaseBillId() {
    return documentId;
  }

  /**
   * Sets purchase bill line ID (alias for lineItemId).
   * Convenience method for backward compatibility.
   */
  public void setPurchaseBillLineId(UUID purchaseBillLineId) {
    this.lineItemId = purchaseBillLineId;
  }

  /**
   * Gets purchase bill line ID (alias for lineItemId).
   * Convenience method for backward compatibility.
   */
  public UUID getPurchaseBillLineId() {
    return lineItemId;
  }
}
