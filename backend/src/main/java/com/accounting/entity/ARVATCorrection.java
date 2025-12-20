package com.accounting.entity;

import java.math.BigDecimal;
import java.time.Instant;
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

/**
 * Tracks manual VAT corrections for sales invoices (AR module).
 * Uses the unified vat_corrections table with document_type = 'SALES_INVOICE'.
 */
@Entity
@Table(name = "vat_corrections")
@org.hibernate.annotations.SQLRestriction("document_type = 'SALES_INVOICE'")
public class ARVATCorrection implements CompanyScopedEntity {

  // Constant for document type discrimination
  private static final String DOCUMENT_TYPE = "SALES_INVOICE";

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @NotNull
  @Column(name = "company_id", nullable = false)
  private Long companyId;

  @Column(name = "document_type", nullable = false, length = 20)
  private String documentType = DOCUMENT_TYPE;

  @NotNull
  @Column(name = "document_id", nullable = false)
  private UUID invoiceId;

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

  @ManyToOne
  @JoinColumn(name = "document_id", insertable = false, updatable = false)
  private SalesInvoice invoice;

  @ManyToOne
  @JoinColumn(name = "line_item_id", insertable = false, updatable = false)
  private SalesInvoiceLine lineItem;

  @PrePersist
  protected void onCreate() {
    Instant now = Instant.now();
    createdAt = now;
    updatedAt = now;
    if (correctedAt == null) {
      correctedAt = now;
    }
    // Ensure document_type is set for AR corrections
    documentType = DOCUMENT_TYPE;
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

  public UUID getInvoiceId() {
    return invoiceId;
  }

  public void setInvoiceId(UUID invoiceId) {
    this.invoiceId = invoiceId;
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

  public SalesInvoice getInvoice() {
    return invoice;
  }

  public SalesInvoiceLine getLineItem() {
    return lineItem;
  }
}
