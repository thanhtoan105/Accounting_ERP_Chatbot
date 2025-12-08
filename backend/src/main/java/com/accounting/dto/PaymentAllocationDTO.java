package com.accounting.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * DTO for payment allocation details.
 */
public class PaymentAllocationDTO {

  private UUID id;
  private UUID paymentId;
  private UUID purchaseBillId;
  private String purchaseBillNumber;
  private LocalDate purchaseBillDate;
  private LocalDate purchaseBillDueDate;
  private BigDecimal purchaseBillTotalAmount;
  private BigDecimal purchaseBillRemainingBalance;
  private BigDecimal allocatedAmount;
  private Integer allocationOrder;
  private Instant createdAt;

  public PaymentAllocationDTO() {}

  // Getters and setters
  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public UUID getPaymentId() {
    return paymentId;
  }

  public void setPaymentId(UUID paymentId) {
    this.paymentId = paymentId;
  }

  public UUID getPurchaseBillId() {
    return purchaseBillId;
  }

  public void setPurchaseBillId(UUID purchaseBillId) {
    this.purchaseBillId = purchaseBillId;
  }

  public String getPurchaseBillNumber() {
    return purchaseBillNumber;
  }

  public void setPurchaseBillNumber(String purchaseBillNumber) {
    this.purchaseBillNumber = purchaseBillNumber;
  }

  public LocalDate getPurchaseBillDate() {
    return purchaseBillDate;
  }

  public void setPurchaseBillDate(LocalDate purchaseBillDate) {
    this.purchaseBillDate = purchaseBillDate;
  }

  public LocalDate getPurchaseBillDueDate() {
    return purchaseBillDueDate;
  }

  public void setPurchaseBillDueDate(LocalDate purchaseBillDueDate) {
    this.purchaseBillDueDate = purchaseBillDueDate;
  }

  public BigDecimal getPurchaseBillTotalAmount() {
    return purchaseBillTotalAmount;
  }

  public void setPurchaseBillTotalAmount(BigDecimal purchaseBillTotalAmount) {
    this.purchaseBillTotalAmount = purchaseBillTotalAmount;
  }

  public BigDecimal getPurchaseBillRemainingBalance() {
    return purchaseBillRemainingBalance;
  }

  public void setPurchaseBillRemainingBalance(BigDecimal purchaseBillRemainingBalance) {
    this.purchaseBillRemainingBalance = purchaseBillRemainingBalance;
  }

  public BigDecimal getAllocatedAmount() {
    return allocatedAmount;
  }

  public void setAllocatedAmount(BigDecimal allocatedAmount) {
    this.allocatedAmount = allocatedAmount;
  }

  public Integer getAllocationOrder() {
    return allocationOrder;
  }

  public void setAllocationOrder(Integer allocationOrder) {
    this.allocationOrder = allocationOrder;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }
}
