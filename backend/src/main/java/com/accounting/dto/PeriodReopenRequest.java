package com.accounting.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * DTO for period reopen requests.
 */
public class PeriodReopenRequest {

  @NotBlank(message = "Reopen reason is required")
  private String reason;

  @NotBlank(message = "Approval metadata is required")
  private String approvalMetadata;

  public PeriodReopenRequest() {}

  public PeriodReopenRequest(String reason, String approvalMetadata) {
    this.reason = reason;
    this.approvalMetadata = approvalMetadata;
  }

  public String getReason() {
    return reason;
  }

  public void setReason(String reason) {
    this.reason = reason;
  }

  public String getApprovalMetadata() {
    return approvalMetadata;
  }

  public void setApprovalMetadata(String approvalMetadata) {
    this.approvalMetadata = approvalMetadata;
  }

  @Override
  public String toString() {
    return "PeriodReopenRequest{" +
        "reason='" + reason + '\'' +
        ", approvalMetadata='" + approvalMetadata + '\'' +
        '}';
  }
}