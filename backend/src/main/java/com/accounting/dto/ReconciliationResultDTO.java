package com.accounting.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * DTO for supplier statement reconciliation results. Contains matched, mismatched, missing, and
 * applied items.
 */
public class ReconciliationResultDTO {

  private Long supplierId;
  private String supplierName;
  private LocalDate reconciliationDate;
  private Integer totalItems;
  private Integer matchedCount;
  private Integer mismatchedCount;
  private Integer missingCount;
  private Integer appliedCount;
  private List<ReconciliationItemDTO> matched = new ArrayList<>();
  private List<ReconciliationItemDTO> mismatched = new ArrayList<>();
  private List<ReconciliationItemDTO> missing = new ArrayList<>();
  private List<ReconciliationItemDTO> applied = new ArrayList<>();

  // Getters and setters
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

  public LocalDate getReconciliationDate() {
    return reconciliationDate;
  }

  public void setReconciliationDate(LocalDate reconciliationDate) {
    this.reconciliationDate = reconciliationDate;
  }

  public Integer getTotalItems() {
    return totalItems;
  }

  public void setTotalItems(Integer totalItems) {
    this.totalItems = totalItems;
  }

  public Integer getMatchedCount() {
    return matchedCount;
  }

  public void setMatchedCount(Integer matchedCount) {
    this.matchedCount = matchedCount;
  }

  public Integer getMismatchedCount() {
    return mismatchedCount;
  }

  public void setMismatchedCount(Integer mismatchedCount) {
    this.mismatchedCount = mismatchedCount;
  }

  public Integer getMissingCount() {
    return missingCount;
  }

  public void setMissingCount(Integer missingCount) {
    this.missingCount = missingCount;
  }

  public Integer getAppliedCount() {
    return appliedCount;
  }

  public void setAppliedCount(Integer appliedCount) {
    this.appliedCount = appliedCount;
  }

  public List<ReconciliationItemDTO> getMatched() {
    return matched;
  }

  public void setMatched(List<ReconciliationItemDTO> matched) {
    this.matched = matched;
  }

  public List<ReconciliationItemDTO> getMismatched() {
    return mismatched;
  }

  public void setMismatched(List<ReconciliationItemDTO> mismatched) {
    this.mismatched = mismatched;
  }

  public List<ReconciliationItemDTO> getMissing() {
    return missing;
  }

  public void setMissing(List<ReconciliationItemDTO> missing) {
    this.missing = missing;
  }

  public List<ReconciliationItemDTO> getApplied() {
    return applied;
  }

  public void setApplied(List<ReconciliationItemDTO> applied) {
    this.applied = applied;
  }

  /**
   * DTO for a reconciliation item.
   */
  public static class ReconciliationItemDTO {

    private UUID billId;
    private String billNumber;
    private LocalDate billDate;
    private BigDecimal supplierAmount;
    private BigDecimal systemAmount;
    private BigDecimal variance;
    private String status; // "MATCHED", "MISMATCHED", "MISSING", "APPLIED"
    private String notes;

    // Getters and setters
    public UUID getBillId() {
      return billId;
    }

    public void setBillId(UUID billId) {
      this.billId = billId;
    }

    public String getBillNumber() {
      return billNumber;
    }

    public void setBillNumber(String billNumber) {
      this.billNumber = billNumber;
    }

    public LocalDate getBillDate() {
      return billDate;
    }

    public void setBillDate(LocalDate billDate) {
      this.billDate = billDate;
    }

    public BigDecimal getSupplierAmount() {
      return supplierAmount;
    }

    public void setSupplierAmount(BigDecimal supplierAmount) {
      this.supplierAmount = supplierAmount;
    }

    public BigDecimal getSystemAmount() {
      return systemAmount;
    }

    public void setSystemAmount(BigDecimal systemAmount) {
      this.systemAmount = systemAmount;
    }

    public BigDecimal getVariance() {
      return variance;
    }

    public void setVariance(BigDecimal variance) {
      this.variance = variance;
    }

    public String getStatus() {
      return status;
    }

    public void setStatus(String status) {
      this.status = status;
    }

    public String getNotes() {
      return notes;
    }

    public void setNotes(String notes) {
      this.notes = notes;
    }
  }
}
