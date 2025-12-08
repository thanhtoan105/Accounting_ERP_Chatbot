package com.accounting.dto;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class ARVATCorrectionCreateRequest {

  @NotNull private UUID invoiceId;

  private UUID lineItemId;

  @NotNull
  @DecimalMin(value = "0.00", inclusive = true, message = "New VAT amount must be positive")
  private BigDecimal newVatAmount;

  @Size(min = 5, max = 500, message = "Reason must be between 5 and 500 characters")
  private String reason;

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
