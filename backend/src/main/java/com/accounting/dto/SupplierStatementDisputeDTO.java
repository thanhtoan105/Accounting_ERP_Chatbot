package com.accounting.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.accounting.entity.SupplierStatementDispute;

/**
 * DTO for supplier statement dispute.
 */
public class SupplierStatementDisputeDTO {

  private UUID id;
  private Long supplierId;
  private String supplierName;
  private String supplierCode;
  private UUID billId;
  private String billNumber;
  private String disputeReason;
  private SupplierStatementDispute.DisputeStatus status;
  private String resolutionNotes;
  private String createdByName;
  private Instant createdAt;
  private String resolvedByName;
  private Instant resolvedAt;
  private BigDecimal disputedAmount;
  private BigDecimal systemAmount;
  private BigDecimal variance;

  // Getters and setters
  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public Long getSupplierId() {
    return supplierId;
  }

  public void setSupplierId(Long supplierId) {
    this.supplierId = supplierId;
  }

  public String getSupplierName() {
    return supplierName;
  }

  public void setSupplierName(String supplierName) {
    this.supplierName = supplierName;
  }

  public String getSupplierCode() {
    return supplierCode;
  }

  public void setSupplierCode(String supplierCode) {
    this.supplierCode = supplierCode;
  }

  public UUID getBillId() {
    return billId;
  }

  public void setBillId(UUID billId) {
    this.billId = billId;
  }

  public String getBillNumber() {
    return billNumber;
  }

  public void setBillNumber(String billNumber) {
    this.billNumber = billNumber;
  }

  public String getDisputeReason() {
    return disputeReason;
  }

  public void setDisputeReason(String disputeReason) {
    this.disputeReason = disputeReason;
  }

  public SupplierStatementDispute.DisputeStatus getStatus() {
    return status;
  }

  public void setStatus(SupplierStatementDispute.DisputeStatus status) {
    this.status = status;
  }

  public String getResolutionNotes() {
    return resolutionNotes;
  }

  public void setResolutionNotes(String resolutionNotes) {
    this.resolutionNotes = resolutionNotes;
  }

  public String getCreatedByName() {
    return createdByName;
  }

  public void setCreatedByName(String createdByName) {
    this.createdByName = createdByName;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public String getResolvedByName() {
    return resolvedByName;
  }

  public void setResolvedByName(String resolvedByName) {
    this.resolvedByName = resolvedByName;
  }

  public Instant getResolvedAt() {
    return resolvedAt;
  }

  public void setResolvedAt(Instant resolvedAt) {
    this.resolvedAt = resolvedAt;
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

  public BigDecimal getVariance() {
    return variance;
  }

  public void setVariance(BigDecimal variance) {
    this.variance = variance;
  }
}
