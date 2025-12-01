package com.accounting.dto;

import java.math.BigDecimal;

/**
 * DTO representing an AR aging report entry for a customer.
 * Contains customer information and aging bucket amounts.
 */
public class ARAgingReportDTO {

    private Long customerId;
    private String customerName;
    private String customerCode;
    private ARAgingBucketDTO buckets;
    private BigDecimal totalOutstanding;
    private boolean hasOverdue;
    private Integer invoiceCount;

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getCustomerCode() {
        return customerCode;
    }

    public void setCustomerCode(String customerCode) {
        this.customerCode = customerCode;
    }

    public ARAgingBucketDTO getBuckets() {
        return buckets;
    }

    public void setBuckets(ARAgingBucketDTO buckets) {
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

    public Integer getInvoiceCount() {
        return invoiceCount;
    }

    public void setInvoiceCount(Integer invoiceCount) {
        this.invoiceCount = invoiceCount;
    }
}
