package com.accounting.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.accounting.entity.PaymentMethod;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * DTO for creating or updating an AR receipt with allocations.
 */
public class ARPaymentCreateRequest {

  @NotNull(message = "Customer ID is required")
  private Long customerId;

  @NotNull(message = "Receipt date is required")
  private LocalDate receiptDate;

  private Long cashAccountId;

  private Long bankAccountId;

  @NotBlank(message = "Payee is required")
  @Size(max = 255, message = "Payee must not exceed 255 characters")
  private String payee;

  @NotNull(message = "Amount is required")
  @Positive(message = "Amount must be positive")
  private BigDecimal amount;

  @Size(max = 500, message = "Reference must not exceed 500 characters")
  private String reference;

  @NotNull(message = "Payment method is required")
  private PaymentMethod paymentMethod;

  @Size(max = 1000, message = "Receipt proof URL must not exceed 1000 characters")
  private String receiptProofUrl;

  private Boolean isStandalone = false;

  @Valid
  private List<ReceiptAllocationRequest> allocations;

  private UUID id; // For updates

  public ARPaymentCreateRequest() {}

  // Getters and setters
  public Long getCustomerId() {
    return customerId;
  }

  public void setCustomerId(Long customerId) {
    this.customerId = customerId;
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

  public Long getBankAccountId() {
    return bankAccountId;
  }

  public void setBankAccountId(Long bankAccountId) {
    this.bankAccountId = bankAccountId;
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

  public List<ReceiptAllocationRequest> getAllocations() {
    return allocations;
  }

  public void setAllocations(List<ReceiptAllocationRequest> allocations) {
    this.allocations = allocations;
  }

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }
}
