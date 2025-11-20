package com.accounting.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

public class VATCorrectionCreateRequest {

  @NotNull private UUID purchaseBillId;

  private UUID purchaseBillLineId;

  @NotNull
  @DecimalMin(value = "0.00", inclusive = true, message = "New VAT amount must be positive")
  private BigDecimal newVatAmount;

  @Size(min = 5, max = 500, message = "Reason must be between 5 and 500 characters")
  private String reason;

  public UUID getPurchaseBillId() {
    return purchaseBillId;
  }

  public void setPurchaseBillId(UUID purchaseBillId) {
    this.purchaseBillId = purchaseBillId;
  }

  public UUID getPurchaseBillLineId() {
    return purchaseBillLineId;
  }

  public void setPurchaseBillLineId(UUID purchaseBillLineId) {
    this.purchaseBillLineId = purchaseBillLineId;
  }

  public BigDecimal getNewVatAmount() {
    return newVatAmount;
  }

  public void setNewVatAmount(BigDecimal newVatAmount) {
    this.newVatAmount = newVatAmount;
  }

  public String getReason() {
    return reason;
  }

  public void setReason(String reason) {
    this.reason = reason;
  }
}

