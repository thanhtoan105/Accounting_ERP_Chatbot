package com.accounting.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * DTO for receipt allocation request.
 */
public class ReceiptAllocationRequest {

  @NotNull(message = "Sales invoice ID is required")
  private UUID salesInvoiceId;

  @NotNull(message = "Allocated amount is required")
  @Positive(message = "Allocated amount must be positive")
  private BigDecimal allocatedAmount;

  public ReceiptAllocationRequest() {}

  public ReceiptAllocationRequest(UUID salesInvoiceId, BigDecimal allocatedAmount) {
    this.salesInvoiceId = salesInvoiceId;
    this.allocatedAmount = allocatedAmount;
  }

  // Getters and setters
  public UUID getSalesInvoiceId() {
    return salesInvoiceId;
  }

  public void setSalesInvoiceId(UUID salesInvoiceId) {
    this.salesInvoiceId = salesInvoiceId;
  }

  public BigDecimal getAllocatedAmount() {
    return allocatedAmount;
  }

  public void setAllocatedAmount(BigDecimal allocatedAmount) {
    this.allocatedAmount = allocatedAmount;
  }
}
