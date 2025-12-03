package com.accounting.dto.audit;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * DTO for requesting audit log purge.
 * Requires Chief Accountant role and will need Admin approval.
 */
public class PurgeRequestDTO {

  @NotNull(message = "dateFrom is required")
  private LocalDate dateFrom;

  @NotNull(message = "dateTo is required")
  private LocalDate dateTo;

  @NotBlank(message = "reason is required")
  @Size(min = 20, max = 1000, message = "reason must be between 20 and 1000 characters")
  private String reason;

  public PurgeRequestDTO() {
  }

  public PurgeRequestDTO(LocalDate dateFrom, LocalDate dateTo, String reason) {
    this.dateFrom = dateFrom;
    this.dateTo = dateTo;
    this.reason = reason;
  }

  public LocalDate getDateFrom() {
    return dateFrom;
  }

  public void setDateFrom(LocalDate dateFrom) {
    this.dateFrom = dateFrom;
  }

  public LocalDate getDateTo() {
    return dateTo;
  }

  public void setDateTo(LocalDate dateTo) {
    this.dateTo = dateTo;
  }

  public String getReason() {
    return reason;
  }

  public void setReason(String reason) {
    this.reason = reason;
  }
}
