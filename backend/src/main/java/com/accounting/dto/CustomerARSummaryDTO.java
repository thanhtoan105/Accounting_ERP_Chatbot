package com.accounting.dto;

import java.math.BigDecimal;

/**
 * DTO for Customer AR (Accounts Receivable) summary.
 * Contains open invoices count, total owed amount, and average payment days.
 * For MVP, this may return placeholder values until Epic 5 (AR Module) is implemented.
 */
public class CustomerARSummaryDTO {

  private Integer openInvoices;
  private BigDecimal totalOwed;
  private Integer averagePaymentDays;

  public CustomerARSummaryDTO() {}

  public CustomerARSummaryDTO(Integer openInvoices, BigDecimal totalOwed, Integer averagePaymentDays) {
    this.openInvoices = openInvoices;
    this.totalOwed = totalOwed;
    this.averagePaymentDays = averagePaymentDays;
  }

  public Integer getOpenInvoices() {
    return openInvoices;
  }

  public void setOpenInvoices(Integer openInvoices) {
    this.openInvoices = openInvoices;
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

