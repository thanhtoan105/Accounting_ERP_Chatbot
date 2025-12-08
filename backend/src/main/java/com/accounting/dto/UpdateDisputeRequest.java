package com.accounting.dto;

import com.accounting.entity.SupplierStatementDispute;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for updating dispute status and resolution.
 */
public class UpdateDisputeRequest {

  @NotNull(message = "Dispute status is required")
  private SupplierStatementDispute.DisputeStatus status;

  @Size(max = 2000, message = "Resolution notes must not exceed 2000 characters")
  private String resolutionNotes;

  // Getters and setters
  public SupplierStatementDispute.DisputeStatus getStatus() {
    return status;
  }

  public void setStatus(SupplierStatementDispute.DisputeStatus status) {
    this.status = status;
  }

  public String getResolutionNotes() {
    return resolutionNotes;
  }

  public void setResolutionNotes(String resolutionNotes) {
    this.resolutionNotes = resolutionNotes;
  }
}
