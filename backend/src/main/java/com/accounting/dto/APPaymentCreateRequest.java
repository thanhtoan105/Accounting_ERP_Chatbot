package com.accounting.dto;

import com.accounting.entity.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * DTO for creating or updating an AP payment with allocations.
 */
public class APPaymentCreateRequest {

  @NotNull(message = "Supplier ID is required")
  private Long supplierId;

  @NotNull(message = "Payment date is required")
  private LocalDate paymentDate;

  private LocalDate dueDate;

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

  @Size(max = 1000, message = "Payment proof URL must not exceed 1000 characters")
  private String paymentProofUrl;

  private Boolean isStandalone = false;

  @Valid
  private List<PaymentAllocationRequest> allocations;

  private UUID id; // For updates

  public APPaymentCreateRequest() {}

  // Getters and setters
  public Long getSupplierId() {
    return supplierId;
  }

  public void setSupplierId(Long supplierId) {
    this.supplierId = supplierId;
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

  public List<PaymentAllocationRequest> getAllocations() {
    return allocations;
  }

  public void setAllocations(List<PaymentAllocationRequest> allocations) {
    this.allocations = allocations;
  }

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }
}

