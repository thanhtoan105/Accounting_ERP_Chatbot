package com.accounting.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * DTO for creating or updating a voucher with its line items.
 */
public class VoucherCreateRequest {

  @NotNull(message = "Voucher date is required")
  private LocalDate date;

  @NotBlank(message = "Description is required")
  @Size(max = 500, message = "Description must not exceed 500 characters")
  private String description;

  private UUID periodId; // Optional - Period ID (auto-determined from date if not provided)

  private String currency = "VND";

  @Valid
  private List<VoucherEntryLineRequest> entryLines;

  /**
   * Legacy payload support – direct ledger lines (each line represents a single debit or credit).
   * Prefer {@link #entryLines} for new functionality.
   */
  @Valid
  private List<VoucherLineDTO> lines;

  public VoucherCreateRequest() {}

  public VoucherCreateRequest(
      LocalDate date,
      String description,
      UUID periodId,
      String currency,
      List<VoucherEntryLineRequest> entryLines,
      List<VoucherLineDTO> lines) {
    this.date = date;
    this.description = description;
    this.periodId = periodId;
    this.currency = currency;
    this.entryLines = entryLines;
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

  public UUID getPeriodId() {
    return periodId;
  }

  public void setPeriodId(UUID periodId) {
    this.periodId = periodId;
  }

  public String getCurrency() {
    return currency;
  }

  public void setCurrency(String currency) {
    this.currency = currency;
  }

  public List<VoucherEntryLineRequest> getEntryLines() {
    return entryLines;
  }

  public void setEntryLines(List<VoucherEntryLineRequest> entryLines) {
    this.entryLines = entryLines;
  }

  public List<VoucherLineDTO> getLines() {
    return lines;
  }

  public void setLines(List<VoucherLineDTO> lines) {
    this.lines = lines;
  }
}
