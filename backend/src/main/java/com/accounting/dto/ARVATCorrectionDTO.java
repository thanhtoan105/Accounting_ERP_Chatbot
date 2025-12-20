package com.accounting.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.accounting.entity.ARVATCorrection.Status;

public class ARVATCorrectionDTO {

  private UUID id;
  private UUID invoiceId;
  private UUID lineItemId;
  private BigDecimal oldVatAmount;
  private BigDecimal newVatAmount;
  private BigDecimal difference;
  private String reason;
  private Status status;
  private Long correctedById;
  private String correctedByName;
  private Instant correctedAt;
  private Long approvedById;
  private String approvedByName;
  private Instant approvedAt;

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
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

  public BigDecimal getDifference() {
    return difference;
  }

  public void setDifference(BigDecimal difference) {
    this.difference = difference;
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

  public String getCorrectedByName() {
    return correctedByName;
  }

  public void setCorrectedByName(String correctedByName) {
    this.correctedByName = correctedByName;
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

  public String getApprovedByName() {
    return approvedByName;
  }

  public void setApprovedByName(String approvedByName) {
    this.approvedByName = approvedByName;
  }

  public Instant getApprovedAt() {
    return approvedAt;
  }

  public void setApprovedAt(Instant approvedAt) {
    this.approvedAt = approvedAt;
  }
}
