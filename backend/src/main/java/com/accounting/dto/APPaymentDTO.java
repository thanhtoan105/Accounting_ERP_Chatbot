package com.accounting.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.accounting.entity.PaymentMethod;
import com.accounting.entity.PaymentStatus;

/**
 * DTO for full APPayment details. Used for single payment retrieval.
 */
public class APPaymentDTO {

  private UUID id;
  private Long companyId;
  private Long supplierId;
  private String supplierName;
  private String supplierCode;
  private String paymentNumber;
  private LocalDate paymentDate;
  private LocalDate dueDate;
  private Long cashAccountId;
  private String cashAccountName;
  private String cashAccountNumber;
  private Long bankAccountId;
  private String bankAccountName;
  private String bankAccountNumber;
  private String payee;
  private BigDecimal amount;
  private String reference;
  private PaymentMethod paymentMethod;
  private String paymentProofUrl;
  private Boolean isStandalone;
  private PaymentStatus status;
  private Long createdById;
  private String createdByName;
  private Long approvedById;
  private String approvedByName;
  private UUID linkedVoucherId;
  private String linkedVoucherNumber;
  private Instant createdAt;
  private Instant updatedAt;
  private Instant postedAt;
  private List<PaymentAllocationDTO> allocations;

  public APPaymentDTO() {}

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

  public String getPaymentNumber() {
    return paymentNumber;
  }

  public void setPaymentNumber(String paymentNumber) {
    this.paymentNumber = paymentNumber;
  }

  public LocalDate getPaymentDate() {
    return paymentDate;
  }

  public void setPaymentDate(LocalDate paymentDate) {
    this.paymentDate = paymentDate;
  }

  public LocalDate getDueDate() {
    return dueDate;
  }

  public void setDueDate(LocalDate dueDate) {
    this.dueDate = dueDate;
  }

  public Long getCashAccountId() {
    return cashAccountId;
  }

  public void setCashAccountId(Long cashAccountId) {
    this.cashAccountId = cashAccountId;
  }

  public String getCashAccountName() {
    return cashAccountName;
  }

  public void setCashAccountName(String cashAccountName) {
    this.cashAccountName = cashAccountName;
  }

  public String getCashAccountNumber() {
    return cashAccountNumber;
  }

  public void setCashAccountNumber(String cashAccountNumber) {
    this.cashAccountNumber = cashAccountNumber;
  }

  public Long getBankAccountId() {
    return bankAccountId;
  }

  public void setBankAccountId(Long bankAccountId) {
    this.bankAccountId = bankAccountId;
  }

  public String getBankAccountName() {
    return bankAccountName;
  }

  public void setBankAccountName(String bankAccountName) {
    this.bankAccountName = bankAccountName;
  }

  public String getBankAccountNumber() {
    return bankAccountNumber;
  }

  public void setBankAccountNumber(String bankAccountNumber) {
    this.bankAccountNumber = bankAccountNumber;
  }

  public String getPayee() {
    return payee;
  }

  public void setPayee(String payee) {
    this.payee = payee;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public void setAmount(BigDecimal amount) {
    this.amount = amount;
  }

  public String getReference() {
    return reference;
  }

  public void setReference(String reference) {
    this.reference = reference;
  }

  public PaymentMethod getPaymentMethod() {
    return paymentMethod;
  }

  public void setPaymentMethod(PaymentMethod paymentMethod) {
    this.paymentMethod = paymentMethod;
  }

  public String getPaymentProofUrl() {
    return paymentProofUrl;
  }

  public void setPaymentProofUrl(String paymentProofUrl) {
    this.paymentProofUrl = paymentProofUrl;
  }

  public Boolean getIsStandalone() {
    return isStandalone;
  }

  public void setIsStandalone(Boolean isStandalone) {
    this.isStandalone = isStandalone;
  }

  public PaymentStatus getStatus() {
    return status;
  }

  public void setStatus(PaymentStatus status) {
    this.status = status;
  }

  public Long getCreatedById() {
    return createdById;
  }

  public void setCreatedById(Long createdById) {
    this.createdById = createdById;
  }

  public String getCreatedByName() {
    return createdByName;
  }

  public void setCreatedByName(String createdByName) {
    this.createdByName = createdByName;
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

  public UUID getLinkedVoucherId() {
    return linkedVoucherId;
  }

  public void setLinkedVoucherId(UUID linkedVoucherId) {
    this.linkedVoucherId = linkedVoucherId;
  }

  public String getLinkedVoucherNumber() {
    return linkedVoucherNumber;
  }

  public void setLinkedVoucherNumber(String linkedVoucherNumber) {
    this.linkedVoucherNumber = linkedVoucherNumber;
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

  public Instant getPostedAt() {
    return postedAt;
  }

  public void setPostedAt(Instant postedAt) {
    this.postedAt = postedAt;
  }

  public List<PaymentAllocationDTO> getAllocations() {
    return allocations;
  }

  public void setAllocations(List<PaymentAllocationDTO> allocations) {
    this.allocations = allocations;
  }
}
