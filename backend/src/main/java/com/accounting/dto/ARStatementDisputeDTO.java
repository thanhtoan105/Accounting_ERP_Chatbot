package com.accounting.dto;

import com.accounting.entity.ARStatementDispute;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO for AR statement dispute record.
 */
public class ARStatementDisputeDTO {

  private UUID id;
  private UUID reconciliationId;
  private UUID invoiceId;
  private String invoiceNumber;
  private BigDecimal systemAmount;
  private BigDecimal customerAmount;
  private BigDecimal variance;
  private ARStatementDispute.VarianceType varianceType;
  private String notes;
  private ARStatementDispute.DisputeStatus status;
  private Instant resolvedAt;
  private Long resolvedById;
  private String resolvedByName;
  private String resolutionNotes;
  private Instant createdAt;
  private Instant updatedAt;

  // Getters and setters
  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public UUID getReconciliationId() {
    return reconciliationId;
  }

  public void setReconciliationId(UUID reconciliationId) {
    this.reconciliationId = reconciliationId;
  }

  public UUID getInvoiceId() {
    return invoiceId;
  }

  public void setInvoiceId(UUID invoiceId) {
    this.invoiceId = invoiceId;
  }

  public String getInvoiceNumber() {
    return invoiceNumber;
  }

  public void setInvoiceNumber(String invoiceNumber) {
    this.invoiceNumber = invoiceNumber;
  }

  public BigDecimal getSystemAmount() {
    return systemAmount;
  }

  public void setSystemAmount(BigDecimal systemAmount) {
    this.systemAmount = systemAmount;
  }

  public BigDecimal getCustomerAmount() {
    return customerAmount;
  }

  public void setCustomerAmount(BigDecimal customerAmount) {
    this.customerAmount = customerAmount;
  }

  public BigDecimal getVariance() {
    return variance;
  }

  public void setVariance(BigDecimal variance) {
    this.variance = variance;
  }

  public ARStatementDispute.VarianceType getVarianceType() {
    return varianceType;
  }

  public void setVarianceType(ARStatementDispute.VarianceType varianceType) {
    this.varianceType = varianceType;
  }

  public String getNotes() {
    return notes;
  }

  public void setNotes(String notes) {
    this.notes = notes;
  }

  public ARStatementDispute.DisputeStatus getStatus() {
    return status;
  }

  public void setStatus(ARStatementDispute.DisputeStatus status) {
    this.status = status;
  }

  public Instant getResolvedAt() {
    return resolvedAt;
  }

  public void setResolvedAt(Instant resolvedAt) {
    this.resolvedAt = resolvedAt;
  }

  public Long getResolvedById() {
    return resolvedById;
  }

  public void setResolvedById(Long resolvedById) {
    this.resolvedById = resolvedById;
  }

  public String getResolvedByName() {
    return resolvedByName;
  }

  public void setResolvedByName(String resolvedByName) {
    this.resolvedByName = resolvedByName;
  }

  public String getResolutionNotes() {
    return resolutionNotes;
  }

  public void setResolutionNotes(String resolutionNotes) {
    this.resolutionNotes = resolutionNotes;
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

