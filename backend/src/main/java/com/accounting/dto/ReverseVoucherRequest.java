package com.accounting.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for reversing a voucher.
 */
public class ReverseVoucherRequest {

  @NotBlank(message = "Description is required")
  private String description;

  @NotBlank(message = "Reason is required for audit")
  private String reason;

  public ReverseVoucherRequest() {}

  public ReverseVoucherRequest(String description, String reason) {
    this.description = description;
    this.reason = reason;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getReason() {
    return reason;
  }

  public void setReason(String reason) {
    this.reason = reason;
  }
}

