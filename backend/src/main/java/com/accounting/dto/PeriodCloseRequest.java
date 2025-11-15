package com.accounting.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * DTO for period close requests.
 */
public class PeriodCloseRequest {

  @NotBlank(message = "Close reason is required")
  private String reason;

  public PeriodCloseRequest() {}

  public PeriodCloseRequest(String reason) {
    this.reason = reason;
  }

  public String getReason() {
    return reason;
  }

  public void setReason(String reason) {
    this.reason = reason;
  }

  @Override
  public String toString() {
    return "PeriodCloseRequest{" +
        "reason='" + reason + '\'' +
        '}';
  }
}