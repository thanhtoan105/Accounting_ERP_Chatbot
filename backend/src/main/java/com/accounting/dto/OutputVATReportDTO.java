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
 * DTO for Output VAT report (AR module). Contains report metadata, line items by invoice, and summary totals by VAT rate.
 * Follows ND123 format for statutory compliance.
 */
public class OutputVATReportDTO {

  private UUID reportId;
  private Long companyId;
  private UUID periodId;
  private Long customerId;
  private String customerName;
  private String customerTaxCode;
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
  
  // Revenue totals by VAT rate (for ND123 format)
  private BigDecimal revenue0pct = BigDecimal.ZERO;
  private BigDecimal revenue5pct = BigDecimal.ZERO;
  private BigDecimal revenue10pct = BigDecimal.ZERO;
  private BigDecimal revenueExempt = BigDecimal.ZERO;
  private BigDecimal totalVatCollected = BigDecimal.ZERO;

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

  public String getCustomerTaxCode() {
    return customerTaxCode;
  }

  public void setCustomerTaxCode(String customerTaxCode) {
    this.customerTaxCode = customerTaxCode;
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

  public BigDecimal getRevenue0pct() {
    return revenue0pct;
  }

  public void setRevenue0pct(BigDecimal revenue0pct) {
    this.revenue0pct = revenue0pct;
  }

  public BigDecimal getRevenue5pct() {
    return revenue5pct;
  }

  public void setRevenue5pct(BigDecimal revenue5pct) {
    this.revenue5pct = revenue5pct;
  }

  public BigDecimal getRevenue10pct() {
    return revenue10pct;
  }

  public void setRevenue10pct(BigDecimal revenue10pct) {
    this.revenue10pct = revenue10pct;
  }

  public BigDecimal getRevenueExempt() {
    return revenueExempt;
  }

  public void setRevenueExempt(BigDecimal revenueExempt) {
    this.revenueExempt = revenueExempt;
  }

  public BigDecimal getTotalVatCollected() {
    return totalVatCollected;
  }

  public void setTotalVatCollected(BigDecimal totalVatCollected) {
    this.totalVatCollected = totalVatCollected;
  }

  /**
   * DTO for report line item (invoice-level aggregation).
   */
  public static class ReportLineItemDTO {
    private UUID invoiceId;
    private String invoiceNumber;
    private LocalDate invoiceDate;
    private Long customerId;
    private String customerName;
    private String customerTaxCode;
    private VatRate vatRate;
    private BigDecimal vatAmount;
    private BigDecimal totalAmount; // Invoice total (including VAT)
    private BigDecimal baseAmount; // Revenue amount (excluding VAT)
    
    // Revenue by VAT rate (for ND123 format)
    private BigDecimal revenue0pct = BigDecimal.ZERO;
    private BigDecimal revenue5pct = BigDecimal.ZERO;
    private BigDecimal revenue10pct = BigDecimal.ZERO;
    private BigDecimal revenueExempt = BigDecimal.ZERO;

    // Getters and setters
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

    public LocalDate getInvoiceDate() {
      return invoiceDate;
    }

    public void setInvoiceDate(LocalDate invoiceDate) {
      this.invoiceDate = invoiceDate;
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

    public String getCustomerTaxCode() {
      return customerTaxCode;
    }

    public void setCustomerTaxCode(String customerTaxCode) {
      this.customerTaxCode = customerTaxCode;
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

    public BigDecimal getRevenue0pct() {
      return revenue0pct;
    }

    public void setRevenue0pct(BigDecimal revenue0pct) {
      this.revenue0pct = revenue0pct;
    }

    public BigDecimal getRevenue5pct() {
      return revenue5pct;
    }

    public void setRevenue5pct(BigDecimal revenue5pct) {
      this.revenue5pct = revenue5pct;
    }

    public BigDecimal getRevenue10pct() {
      return revenue10pct;
    }

    public void setRevenue10pct(BigDecimal revenue10pct) {
      this.revenue10pct = revenue10pct;
    }

    public BigDecimal getRevenueExempt() {
      return revenueExempt;
    }

    public void setRevenueExempt(BigDecimal revenueExempt) {
      this.revenueExempt = revenueExempt;
    }
  }
}

