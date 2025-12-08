package com.accounting.dto;

import java.time.LocalDate;

import com.accounting.entity.SupplierStatementHistory;

import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for generating supplier statement.
 */
public class GenerateStatementRequest {

  @NotNull(message = "Supplier ID is required")
  private Long supplierId;

  @NotNull(message = "Statement type is required")
  private SupplierStatementHistory.StatementType statementType;

  @NotNull(message = "Start date is required")
  private LocalDate startDate;

  @NotNull(message = "End date is required")
  private LocalDate endDate;

  @NotNull(message = "Export format is required")
  private SupplierStatementHistory.ExportFormat format;

  // Getters and setters
  public Long getSupplierId() {
    return supplierId;
  }

  public void setSupplierId(Long supplierId) {
    this.supplierId = supplierId;
  }

  public SupplierStatementHistory.StatementType getStatementType() {
    return statementType;
  }

  public void setStatementType(SupplierStatementHistory.StatementType statementType) {
    this.statementType = statementType;
  }

  public LocalDate getStartDate() {
    return startDate;
  }

  public void setStartDate(LocalDate startDate) {
    this.startDate = startDate;
  }

  public LocalDate getEndDate() {
    return endDate;
  }

  public void setEndDate(LocalDate endDate) {
    this.endDate = endDate;
  }

  public SupplierStatementHistory.ExportFormat getFormat() {
    return format;
  }

  public void setFormat(SupplierStatementHistory.ExportFormat format) {
    this.format = format;
  }
}
