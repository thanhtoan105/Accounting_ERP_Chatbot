package com.accounting.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * DTO for payment allocation request (manual allocation override).
 */
public class PaymentAllocationRequest {

  @NotNull(message = "Purchase bill ID is required")
  private UUID purchaseBillId;

  @NotNull(message = "Allocated amount is required")
  @Positive(message = "Allocated amount must be positive")
  private BigDecimal allocatedAmount;

  public PaymentAllocationRequest() {}

  public PaymentAllocationRequest(UUID purchaseBillId, BigDecimal allocatedAmount) {
    this.purchaseBillId = purchaseBillId;
    this.allocatedAmount = allocatedAmount;
  }

  // Getters and setters
  public UUID getPurchaseBillId() {
    return purchaseBillId;
  }

  public void setPurchaseBillId(UUID purchaseBillId) {
    this.purchaseBillId = purchaseBillId;
  }

  public BigDecimal getAllocatedAmount() {
    return allocatedAmount;
  }

  public void setAllocatedAmount(BigDecimal allocatedAmount) {
    this.allocatedAmount = allocatedAmount;
  }
}

