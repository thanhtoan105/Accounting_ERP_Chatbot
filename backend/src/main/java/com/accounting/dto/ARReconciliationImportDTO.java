package com.accounting.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * DTO for AR reconciliation import results.
 */
public class ARReconciliationImportDTO {

  private UUID reconciliationId;
  private Long customerId;
  private String customerName;
  private Integer matchedCount;
  private Integer mismatchCount;
  private List<ReconciliationMismatchDTO> mismatches = new ArrayList<>();

  // Getters and setters
  public UUID getReconciliationId() {
    return reconciliationId;
  }

  public void setReconciliationId(UUID reconciliationId) {
    this.reconciliationId = reconciliationId;
  }

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

  public Integer getMatchedCount() {
    return matchedCount;
  }

  public void setMatchedCount(Integer matchedCount) {
    this.matchedCount = matchedCount;
  }

  public Integer getMismatchCount() {
    return mismatchCount;
  }

  public void setMismatchCount(Integer mismatchCount) {
    this.mismatchCount = mismatchCount;
  }

  public List<ReconciliationMismatchDTO> getMismatches() {
    return mismatches;
  }

  public void setMismatches(List<ReconciliationMismatchDTO> mismatches) {
    this.mismatches = mismatches;
  }

  /**
   * DTO for a reconciliation mismatch.
   */
  public static class ReconciliationMismatchDTO {
    private UUID invoiceId;
    private String invoiceNumber;
    private BigDecimal systemAmount;
    private BigDecimal customerAmount;
    private BigDecimal variance;
    private String varianceType; // "SIGNIFICANT" or "ROUNDING"
    private String notes;

    public UUID getInvoiceId() {
      return invoiceId;
    }

    public void setInvoiceId(UUID invoiceId) {
      this.invoiceId = invoiceId;
    }

    public String getInvoiceNumber() {
      return invoiceNumber;
    }

    public void setInvoiceNumber(String invoiceNumber) {
      this.invoiceNumber = invoiceNumber;
    }

    public BigDecimal getSystemAmount() {
      return systemAmount;
    }

    public void setSystemAmount(BigDecimal systemAmount) {
      this.systemAmount = systemAmount;
    }

    public BigDecimal getCustomerAmount() {
      return customerAmount;
    }

    public void setCustomerAmount(BigDecimal customerAmount) {
      this.customerAmount = customerAmount;
    }

    public BigDecimal getVariance() {
      return variance;
    }

    public void setVariance(BigDecimal variance) {
      this.variance = variance;
    }

    public String getVarianceType() {
      return varianceType;
    }

    public void setVarianceType(String varianceType) {
      this.varianceType = varianceType;
    }

    public String getNotes() {
      return notes;
    }

    public void setNotes(String notes) {
      this.notes = notes;
    }
  }
}
