package com.accounting.dto;

import java.math.BigDecimal;

/**
 * DTO representing an AP aging report entry for a supplier.
 */
public class APAgingReportDTO {

  private Long supplierId;
  private String supplierName;
  private String supplierCode;
  private APAgingBucketDTO buckets;
  private BigDecimal totalOutstanding;
  private boolean hasOverdue;

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

  public String getSupplierCode() {
    return supplierCode;
  }

  public void setSupplierCode(String supplierCode) {
    this.supplierCode = supplierCode;
  }

  public APAgingBucketDTO getBuckets() {
    return buckets;
  }

  public void setBuckets(APAgingBucketDTO buckets) {
    this.buckets = buckets;
  }

  public BigDecimal getTotalOutstanding() {
    return totalOutstanding;
  }

  public void setTotalOutstanding(BigDecimal totalOutstanding) {
    this.totalOutstanding = totalOutstanding;
  }

  public boolean isHasOverdue() {
    return hasOverdue;
  }

  public void setHasOverdue(boolean hasOverdue) {
    this.hasOverdue = hasOverdue;
  }
}

