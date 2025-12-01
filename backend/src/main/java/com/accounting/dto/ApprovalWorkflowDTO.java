package com.accounting.dto;

import com.accounting.entity.ApprovalWorkflowStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO for ApprovalWorkflow entity.
 * Used for API responses and inter-service communication.
 */
public class ApprovalWorkflowDTO {

  private UUID id;
  private Long companyId;
  private UUID purchaseBillId;
  private UUID salesInvoiceId;
  private Long createdById;
  private Long approvedById;
  private ApprovalWorkflowStatus status;
  private BigDecimal thresholdAmount;
  private BigDecimal billAmount;
  private Boolean isSensitive;
  private String approvalReason;
  private String rejectionReason;
  private Instant createdAt;
  private Instant updatedAt;
  private Instant approvedAt;
  private Instant rejectedAt;

  // Nested user info for convenience
  private String createdByName;
  private String approvedByName;

  // Nested bill/invoice info for convenience
  private String billNumber;
  private String supplierName;
  private String invoiceNumber;
  private String customerName;

  public ApprovalWorkflowDTO() {
  }

  public ApprovalWorkflowDTO(
      UUID id,
      Long companyId,
      UUID purchaseBillId,
      Long createdById,
      Long approvedById,
      ApprovalWorkflowStatus status,
      BigDecimal thresholdAmount,
      BigDecimal billAmount,
      Boolean isSensitive,
      String approvalReason,
      String rejectionReason,
      Instant createdAt,
      Instant updatedAt,
      Instant approvedAt,
      Instant rejectedAt) {
    this.id = id;
    this.companyId = companyId;
    this.purchaseBillId = purchaseBillId;
    this.salesInvoiceId = null;
    this.createdById = createdById;
    this.approvedById = approvedById;
    this.status = status;
    this.thresholdAmount = thresholdAmount;
    this.billAmount = billAmount;
    this.isSensitive = isSensitive;
    this.approvalReason = approvalReason;
    this.rejectionReason = rejectionReason;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
    this.approvedAt = approvedAt;
    this.rejectedAt = rejectedAt;
  }

  // Getters and setters
  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public Long getCompanyId() {
    return companyId;
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

  public UUID getSalesInvoiceId() {
    return salesInvoiceId;
  }

  public void setSalesInvoiceId(UUID salesInvoiceId) {
    this.salesInvoiceId = salesInvoiceId;
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

  public ApprovalWorkflowStatus getStatus() {
    return status;
  }

  public void setStatus(ApprovalWorkflowStatus status) {
    this.status = status;
  }

  public BigDecimal getThresholdAmount() {
    return thresholdAmount;
  }

  public void setThresholdAmount(BigDecimal thresholdAmount) {
    this.thresholdAmount = thresholdAmount;
  }

  public BigDecimal getBillAmount() {
    return billAmount;
  }

  public void setBillAmount(BigDecimal billAmount) {
    this.billAmount = billAmount;
  }

  public Boolean getIsSensitive() {
    return isSensitive;
  }

  public void setIsSensitive(Boolean isSensitive) {
    this.isSensitive = isSensitive;
  }

  public String getApprovalReason() {
    return approvalReason;
  }

  public void setApprovalReason(String approvalReason) {
    this.approvalReason = approvalReason;
  }

  public String getRejectionReason() {
    return rejectionReason;
  }

  public void setRejectionReason(String rejectionReason) {
    this.rejectionReason = rejectionReason;
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

  public Instant getApprovedAt() {
    return approvedAt;
  }

  public void setApprovedAt(Instant approvedAt) {
    this.approvedAt = approvedAt;
  }

  public Instant getRejectedAt() {
    return rejectedAt;
  }

  public void setRejectedAt(Instant rejectedAt) {
    this.rejectedAt = rejectedAt;
  }

  public String getCreatedByName() {
    return createdByName;
  }

  public void setCreatedByName(String createdByName) {
    this.createdByName = createdByName;
  }

  public String getApprovedByName() {
    return approvedByName;
  }

  public void setApprovedByName(String approvedByName) {
    this.approvedByName = approvedByName;
  }

  public String getBillNumber() {
    return billNumber;
  }

  public void setBillNumber(String billNumber) {
    this.billNumber = billNumber;
  }

  public String getSupplierName() {
    return supplierName;
  }

  public void setSupplierName(String supplierName) {
    this.supplierName = supplierName;
  }

  public String getInvoiceNumber() {
    return invoiceNumber;
  }

  public void setInvoiceNumber(String invoiceNumber) {
    this.invoiceNumber = invoiceNumber;
  }

  public String getCustomerName() {
    return customerName;
  }

  public void setCustomerName(String customerName) {
    this.customerName = customerName;
  }
}
