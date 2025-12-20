package com.accounting.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.accounting.entity.PaymentMethod;
import com.accounting.entity.ReceiptStatus;

/**
 * DTO for full ARPayment (receipt) details. Used for single receipt retrieval.
 */
public class ARPaymentDTO {

  private UUID id;
  private Long companyId;
  private Long customerId;
  private String customerName;
  private String customerCode;
  private String receiptNumber;
  private LocalDate receiptDate;
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
  private String receiptProofUrl;
  private Boolean isStandalone;
  private ReceiptStatus status;
  private Long createdById;
  private String createdByName;
  private Long postedById;
  private String postedByName;
  private UUID linkedVoucherId;
  private String linkedVoucherNumber;
  private String reversalReason;
  private UUID originalReceiptId;
  private String originalReceiptNumber;
  private UUID reversingReceiptId;
  private String reversingReceiptNumber;
  private Instant createdAt;
  private Instant updatedAt;
  private Instant postedAt;
  private List<ReceiptAllocationDTO> allocations;

  public ARPaymentDTO() {}

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

  public Long getCustomerId() {
    return customerId;
  }

  public void setCustomerId(Long customerId) {
    this.customerId = customerId;
  }

  public String getCustomerName() {
    return customerName;
  }

  public void setCustomerName(String customerName) {
    this.customerName = customerName;
  }

  public String getCustomerCode() {
    return customerCode;
  }

  public void setCustomerCode(String customerCode) {
    this.customerCode = customerCode;
  }

  public String getReceiptNumber() {
    return receiptNumber;
  }

  public void setReceiptNumber(String receiptNumber) {
    this.receiptNumber = receiptNumber;
  }

  public LocalDate getReceiptDate() {
    return receiptDate;
  }

  public void setReceiptDate(LocalDate receiptDate) {
    this.receiptDate = receiptDate;
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

  public String getReceiptProofUrl() {
    return receiptProofUrl;
  }

  public void setReceiptProofUrl(String receiptProofUrl) {
    this.receiptProofUrl = receiptProofUrl;
  }

  public Boolean getIsStandalone() {
    return isStandalone;
  }

  public void setIsStandalone(Boolean isStandalone) {
    this.isStandalone = isStandalone;
  }

  public ReceiptStatus getStatus() {
    return status;
  }

  public void setStatus(ReceiptStatus status) {
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

  public Long getPostedById() {
    return postedById;
  }

  public void setPostedById(Long postedById) {
    this.postedById = postedById;
  }

  public String getPostedByName() {
    return postedByName;
  }

  public void setPostedByName(String postedByName) {
    this.postedByName = postedByName;
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

  public String getReversalReason() {
    return reversalReason;
  }

  public void setReversalReason(String reversalReason) {
    this.reversalReason = reversalReason;
  }

  public UUID getOriginalReceiptId() {
    return originalReceiptId;
  }

  public void setOriginalReceiptId(UUID originalReceiptId) {
    this.originalReceiptId = originalReceiptId;
  }

  public String getOriginalReceiptNumber() {
    return originalReceiptNumber;
  }

  public void setOriginalReceiptNumber(String originalReceiptNumber) {
    this.originalReceiptNumber = originalReceiptNumber;
  }

  public UUID getReversingReceiptId() {
    return reversingReceiptId;
  }

  public void setReversingReceiptId(UUID reversingReceiptId) {
    this.reversingReceiptId = reversingReceiptId;
  }

  public String getReversingReceiptNumber() {
    return reversingReceiptNumber;
  }

  public void setReversingReceiptNumber(String reversingReceiptNumber) {
    this.reversingReceiptNumber = reversingReceiptNumber;
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

  public List<ReceiptAllocationDTO> getAllocations() {
    return allocations;
  }

  public void setAllocations(List<ReceiptAllocationDTO> allocations) {
    this.allocations = allocations;
  }
}
