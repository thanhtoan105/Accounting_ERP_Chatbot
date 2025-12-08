package com.accounting.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for unposting a voucher.
 */
public class UnpostVoucherRequest {

  @NotBlank(message = "Reason is required for audit")
  private String reason;

  public UnpostVoucherRequest() {}

  public UnpostVoucherRequest(String reason) {
    this.reason = reason;
  }

  public String getReason() {
    return reason;
  }

  public void setReason(String reason) {
    this.reason = reason;
  }
}
