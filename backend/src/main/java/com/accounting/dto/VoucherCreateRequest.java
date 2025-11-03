package com.accounting.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * DTO for creating or updating a voucher with its line items.
 */
public class VoucherCreateRequest {

  @NotNull(message = "Voucher date is required")
  private LocalDate date;

  @NotBlank(message = "Description is required")
  @Size(max = 500, message = "Description must not exceed 500 characters")
  private String description;

  private Long periodId; // Optional - Period ID

  @NotNull(message = "At least one line item is required")
  @Size(min = 1, message = "At least one line item is required")
  @Valid
  private List<VoucherLineDTO> lines;

  public VoucherCreateRequest() {}

  public VoucherCreateRequest(LocalDate date, String description, Long periodId,
      List<VoucherLineDTO> lines) {
    this.date = date;
    this.description = description;
    this.periodId = periodId;
    this.lines = lines;
  }

  // Getters and setters
  public LocalDate getDate() {
    return date;
  }

  public void setDate(LocalDate date) {
    this.date = date;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public Long getPeriodId() {
    return periodId;
  }

  public void setPeriodId(Long periodId) {
    this.periodId = periodId;
  }

  public List<VoucherLineDTO> getLines() {
    return lines;
  }

  public void setLines(List<VoucherLineDTO> lines) {
    this.lines = lines;
  }
}

