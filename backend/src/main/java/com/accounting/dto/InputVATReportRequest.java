package com.accounting.dto;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.constraints.Size;

/**
 * Request payload for generating input VAT reports.
 */
public class InputVATReportRequest {

  private UUID periodId;
  private Long supplierId;

  @Size(max = 50)
  private String vatClass;

  private LocalDate startDate;
  private LocalDate endDate;

  private Map<String, Object> filters = new HashMap<>();

  public UUID getPeriodId() {
    return periodId;
  }

  public void setPeriodId(UUID periodId) {
    this.periodId = periodId;
  }

  public Long getSupplierId() {
    return supplierId;
  }

  public void setSupplierId(Long supplierId) {
    this.supplierId = supplierId;
  }

  public String getVatClass() {
    return vatClass;
  }

  public void setVatClass(String vatClass) {
    this.vatClass = vatClass;
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

  public Map<String, Object> getFilters() {
    return filters;
  }

  public void setFilters(Map<String, Object> filters) {
    this.filters = filters != null ? filters : new HashMap<>();
  }
}
