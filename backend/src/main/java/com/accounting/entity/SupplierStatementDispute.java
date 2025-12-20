package com.accounting.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

/**
 * SupplierStatementDispute entity for tracking discrepancies found during
 * supplier statement
 * reconciliation. Extends StatementDispute with party_type = 'SUPPLIER'.
 */
@Entity
@DiscriminatorValue("SUPPLIER")
public class SupplierStatementDispute extends StatementDispute {

  // Supplier relationship
  @ManyToOne
  @JoinColumn(name = "party_id", insertable = false, updatable = false)
  private Supplier supplier;

  // Bill relationship
  @ManyToOne
  @JoinColumn(name = "document_id", insertable = false, updatable = false)
  private PurchaseBill purchaseBill;

  @Override
  public StatementPartyType getPartyType() {
    return StatementPartyType.SUPPLIER;
  }

  // Enum for dispute status (kept for backward compatibility)
  public enum DisputeStatus {
    OPEN,
    IN_PROGRESS,
    RESOLVED,
    REJECTED
  }

  // Supplier-specific getters/setters
  public Long getSupplierId() {
    return getPartyId();
  }

  public void setSupplierId(Long supplierId) {
    setPartyId(supplierId);
  }

  public UUID getBillId() {
    return getDocumentId();
  }

  public void setBillId(UUID billId) {
    setDocumentId(billId);
  }

  public String getBillNumber() {
    return getDocumentNumber();
  }

  public void setBillNumber(String billNumber) {
    setDocumentNumber(billNumber);
  }

  public BigDecimal getDisputedAmount() {
    return getCounterpartyAmount();
  }

  public void setDisputedAmount(BigDecimal disputedAmount) {
    setCounterpartyAmount(disputedAmount);
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

  // Backward compatibility aliases
  public Long getCreatedBy() {
    return getCreatedById();
  }

  public void setCreatedBy(Long createdBy) {
    setCreatedById(createdBy);
  }

  public Long getResolvedByUserId() {
    return getResolvedById();
  }

  public void setResolvedByUserId(Long resolvedBy) {
    setResolvedById(resolvedBy);
  }

  public User getResolvedByUser() {
    return super.getResolvedBy();
  }

  public void setResolvedByUser(User user) {
    super.setResolvedBy(user);
  }

  // Status conversion helpers
  public DisputeStatus getStatusEnum() {
    String st = getStatus();
    if (st == null)
      return DisputeStatus.OPEN;
    try {
      return DisputeStatus.valueOf(st);
    } catch (IllegalArgumentException e) {
      return DisputeStatus.OPEN;
    }
  }

  public void setStatusEnum(DisputeStatus status) {
    setStatus(status != null ? status.name() : "OPEN");
  }

  /**
   * Resolves the dispute with resolution notes and resolved by user.
   */
  public void resolve(Long resolvedByUserId, String notes) {
    setStatusEnum(DisputeStatus.RESOLVED);
    setResolvedById(resolvedByUserId);
    setResolvedAt(Instant.now());
    setResolutionNotes(notes);
  }

  /**
   * Rejects the dispute with resolution notes and resolved by user.
   */
  public void reject(Long resolvedByUserId, String notes) {
    setStatusEnum(DisputeStatus.REJECTED);
    setResolvedById(resolvedByUserId);
    setResolvedAt(Instant.now());
    setResolutionNotes(notes);
  }

  /**
   * Checks if the dispute is resolved or rejected.
   */
  public boolean isClosed() {
    return getStatusEnum() == DisputeStatus.RESOLVED || getStatusEnum() == DisputeStatus.REJECTED;
  }
}
