package com.accounting.dto;

import java.math.BigDecimal;

/**
 * DTO representing an overdue supplier for dashboard badge.
 */
public class OverdueSupplierDTO {

  private Long supplierId;
  private String supplierName;
  private BigDecimal overdueAmount;
  private int overdueDays;

  public Long getSupplierId() {
    return supplierId;
  }

  public void setSupplierId(Long supplierId) {
    this.supplierId = supplierId;
  }

  public String getSupplierName() {
    return supplierName;
  }

  public void setSupplierName(String supplierName) {
    this.supplierName = supplierName;
  }

  public BigDecimal getOverdueAmount() {
    return overdueAmount;
  }

  public void setOverdueAmount(BigDecimal overdueAmount) {
    this.overdueAmount = overdueAmount;
  }

  public int getOverdueDays() {
    return overdueDays;
  }

  public void setOverdueDays(int overdueDays) {
    this.overdueDays = overdueDays;
  }
}

