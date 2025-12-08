package com.accounting.dto;

import java.math.BigDecimal;

/**
 * DTO for Supplier AP (Accounts Payable) summary.
 * Contains open bills, total owed, and average payment days.
 */
public class SupplierAPSummaryDTO {

  private Integer openBills;
  private BigDecimal totalOwed;
  private Integer averagePaymentDays;

  public SupplierAPSummaryDTO() {}

  public SupplierAPSummaryDTO(Integer openBills, BigDecimal totalOwed, Integer averagePaymentDays) {
    this.openBills = openBills;
    this.totalOwed = totalOwed;
    this.averagePaymentDays = averagePaymentDays;
  }

  public Integer getOpenBills() {
    return openBills;
  }

  public void setOpenBills(Integer openBills) {
    this.openBills = openBills;
  }

  public BigDecimal getTotalOwed() {
    return totalOwed;
  }

  public void setTotalOwed(BigDecimal totalOwed) {
    this.totalOwed = totalOwed;
  }

  public Integer getAveragePaymentDays() {
    return averagePaymentDays;
  }

  public void setAveragePaymentDays(Integer averagePaymentDays) {
    this.averagePaymentDays = averagePaymentDays;
  }
}
