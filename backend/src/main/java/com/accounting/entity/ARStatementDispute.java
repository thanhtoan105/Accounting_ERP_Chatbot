package com.accounting.entity;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * ARStatementDispute entity for tracking discrepancies found during customer
 * statement
 * reconciliation. Extends StatementDispute with party_type = 'CUSTOMER'.
 */
@Entity
@DiscriminatorValue("CUSTOMER")
public class ARStatementDispute extends StatementDispute {

  // Invoice relationship
  @ManyToOne
  @JoinColumn(name = "document_id", insertable = false, updatable = false)
  private SalesInvoice invoice;

  @Override
  public StatementPartyType getPartyType() {
    return StatementPartyType.CUSTOMER;
  }

  // Enum for variance type (kept for backward compatibility)
  public enum VarianceType {
    SIGNIFICANT, // variance > 1000 VND (red)
    ROUNDING // variance <= 1000 VND (yellow)
  }

  // Enum for dispute status (kept for backward compatibility)
  public enum DisputeStatus {
    OPEN,
    RESOLVED
  }

  // Customer-specific getters/setters
  public UUID getInvoiceId() {
    return getDocumentId();
  }

  public void setInvoiceId(UUID invoiceId) {
    setDocumentId(invoiceId);
  }

  public String getInvoiceNumber() {
    return getDocumentNumber();
  }

  public void setInvoiceNumber(String invoiceNumber) {
    setDocumentNumber(invoiceNumber);
  }

  public BigDecimal getCustomerAmount() {
    return getCounterpartyAmount();
  }

  public void setCustomerAmount(BigDecimal customerAmount) {
    setCounterpartyAmount(customerAmount);
  }

  public SalesInvoice getInvoice() {
    return invoice;
  }

  public void setInvoice(SalesInvoice invoice) {
    this.invoice = invoice;
  }

  // Variance type conversion helpers
  public VarianceType getVarianceTypeEnum() {
    String vt = getVarianceType();
    if (vt == null)
      return null;
    try {
      return VarianceType.valueOf(vt);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  public void setVarianceTypeEnum(VarianceType varianceType) {
    setVarianceType(varianceType != null ? varianceType.name() : null);
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
    setResolvedAt(java.time.Instant.now());
    setResolutionNotes(notes);
  }

  /**
   * Checks if the dispute is resolved.
   */
  public boolean isResolved() {
    return getStatusEnum() == DisputeStatus.RESOLVED;
  }
}
