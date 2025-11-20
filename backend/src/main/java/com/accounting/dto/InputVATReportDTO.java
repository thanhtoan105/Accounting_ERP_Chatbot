package com.accounting.dto;

import com.accounting.entity.VATReportHistory;
import com.accounting.entity.VatRate;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * DTO for Input VAT report. Contains report metadata, line items by bill, and summary totals by VAT
 * rate.
 */
public class InputVATReportDTO {

  private UUID reportId;
  private Long companyId;
  private UUID periodId;
  private Long supplierId;
  private String supplierName;
  private String supplierCode;
  private String vatClass;
  private LocalDate startDate;
  private LocalDate endDate;
  private Instant generationDate;
  private String generatedByName;
  private VATReportHistory.ExportFormat format;
  private List<ReportLineItemDTO> items = new ArrayList<>();
  private Map<VatRate, BigDecimal> totalVATByRate = new EnumMap<>(VatRate.class);
  private BigDecimal grandTotalVAT = BigDecimal.ZERO;
  private BigDecimal grandTotalAmount = BigDecimal.ZERO;

  // Getters and setters
  public UUID getReportId() {
    return reportId;
  }

  public void setReportId(UUID reportId) {
    this.reportId = reportId;
  }

  public Long getCompanyId() {
    return companyId;
  }

  public void setCompanyId(Long companyId) {
    this.companyId = companyId;
  }

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

  public Instant getGenerationDate() {
    return generationDate;
  }

  public void setGenerationDate(Instant generationDate) {
    this.generationDate = generationDate;
  }

  public String getGeneratedByName() {
    return generatedByName;
  }

  public void setGeneratedByName(String generatedByName) {
    this.generatedByName = generatedByName;
  }

  public VATReportHistory.ExportFormat getFormat() {
    return format;
  }

  public void setFormat(VATReportHistory.ExportFormat format) {
    this.format = format;
  }

  public List<ReportLineItemDTO> getItems() {
    return items;
  }

  public void setItems(List<ReportLineItemDTO> items) {
    this.items = items;
  }

  public Map<VatRate, BigDecimal> getTotalVATByRate() {
    return totalVATByRate;
  }

  public void setTotalVATByRate(Map<VatRate, BigDecimal> totalVATByRate) {
    this.totalVATByRate = totalVATByRate;
  }

  public BigDecimal getGrandTotalVAT() {
    return grandTotalVAT;
  }

  public void setGrandTotalVAT(BigDecimal grandTotalVAT) {
    this.grandTotalVAT = grandTotalVAT;
  }

  public BigDecimal getGrandTotalAmount() {
    return grandTotalAmount;
  }

  public void setGrandTotalAmount(BigDecimal grandTotalAmount) {
    this.grandTotalAmount = grandTotalAmount;
  }

  /**
   * DTO for report line item (bill-level aggregation).
   */
  public static class ReportLineItemDTO {
    private UUID billId;
    private String billNumber;
    private LocalDate billDate;
    private Long supplierId;
    private String supplierName;
    private String supplierCode;
    private VatRate vatRate;
    private BigDecimal vatAmount;
    private BigDecimal totalAmount; // Bill total (including VAT)
    private BigDecimal baseAmount; // Bill total excluding VAT

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

    public VatRate getVatRate() {
      return vatRate;
    }

    public void setVatRate(VatRate vatRate) {
      this.vatRate = vatRate;
    }

    public BigDecimal getVatAmount() {
      return vatAmount;
    }

    public void setVatAmount(BigDecimal vatAmount) {
      this.vatAmount = vatAmount;
    }

    public BigDecimal getTotalAmount() {
      return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
      this.totalAmount = totalAmount;
    }

    public BigDecimal getBaseAmount() {
      return baseAmount;
    }

    public void setBaseAmount(BigDecimal baseAmount) {
      this.baseAmount = baseAmount;
    }
  }
}

