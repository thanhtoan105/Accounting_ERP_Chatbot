package com.accounting.dto.report;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.accounting.enums.ReportType;

/**
 * DTO representing a complete financial report (Balance Sheet or Income Statement).
 * Contains report metadata and structured sections with line items.
 * Follows TT200 format for statutory compliance.
 */
public class FinancialReportDTO {

  private UUID reportId; // Unique report identifier
  private Long companyId; // Company ID
  private String companyName; // Company name
  private String taxCode; // Company tax code
  private UUID periodId; // Accounting period ID
  private LocalDate startDate; // Period start date
  private LocalDate endDate; // Period end date
  private String currency; // Currency code (e.g., "VND", "USD")
  private ReportType reportType; // Report type (BALANCE_SHEET or INCOME_STATEMENT)
  private Instant generationDate; // When the report was generated
  private String generatedByName; // Name of user who generated the report
  private List<ReportSectionDTO> sections; // Report sections (Assets, Liabilities, etc.)

  // Summary totals
  private BigDecimal totalAssets; // For Balance Sheet
  private BigDecimal totalLiabilities; // For Balance Sheet
  private BigDecimal totalEquity; // For Balance Sheet
  private BigDecimal totalRevenue; // For Income Statement
  private BigDecimal totalExpenses; // For Income Statement
  private BigDecimal netIncome; // For Income Statement (Revenue - Expenses)

  public FinancialReportDTO() {
    this.currency = "VND";
    this.sections = new ArrayList<>();
    this.totalAssets = BigDecimal.ZERO;
    this.totalLiabilities = BigDecimal.ZERO;
    this.totalEquity = BigDecimal.ZERO;
    this.totalRevenue = BigDecimal.ZERO;
    this.totalExpenses = BigDecimal.ZERO;
    this.netIncome = BigDecimal.ZERO;
  }

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

  public String getCompanyName() {
    return companyName;
  }

  public void setCompanyName(String companyName) {
    this.companyName = companyName;
  }

  public String getTaxCode() {
    return taxCode;
  }

  public void setTaxCode(String taxCode) {
    this.taxCode = taxCode;
  }

  public UUID getPeriodId() {
    return periodId;
  }

  public void setPeriodId(UUID periodId) {
    this.periodId = periodId;
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

  public String getCurrency() {
    return currency;
  }

  public void setCurrency(String currency) {
    this.currency = currency;
  }

  public ReportType getReportType() {
    return reportType;
  }

  public void setReportType(ReportType reportType) {
    this.reportType = reportType;
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

  public List<ReportSectionDTO> getSections() {
    return sections;
  }

  public void setSections(List<ReportSectionDTO> sections) {
    this.sections = sections != null ? sections : new ArrayList<>();
  }

  public BigDecimal getTotalAssets() {
    return totalAssets;
  }

  public void setTotalAssets(BigDecimal totalAssets) {
    this.totalAssets = totalAssets != null ? totalAssets : BigDecimal.ZERO;
  }

  public BigDecimal getTotalLiabilities() {
    return totalLiabilities;
  }

  public void setTotalLiabilities(BigDecimal totalLiabilities) {
    this.totalLiabilities = totalLiabilities != null ? totalLiabilities : BigDecimal.ZERO;
  }

  public BigDecimal getTotalEquity() {
    return totalEquity;
  }

  public void setTotalEquity(BigDecimal totalEquity) {
    this.totalEquity = totalEquity != null ? totalEquity : BigDecimal.ZERO;
  }

  public BigDecimal getTotalRevenue() {
    return totalRevenue;
  }

  public void setTotalRevenue(BigDecimal totalRevenue) {
    this.totalRevenue = totalRevenue != null ? totalRevenue : BigDecimal.ZERO;
  }

  public BigDecimal getTotalExpenses() {
    return totalExpenses;
  }

  public void setTotalExpenses(BigDecimal totalExpenses) {
    this.totalExpenses = totalExpenses != null ? totalExpenses : BigDecimal.ZERO;
  }

  public BigDecimal getNetIncome() {
    return netIncome;
  }

  public void setNetIncome(BigDecimal netIncome) {
    this.netIncome = netIncome != null ? netIncome : BigDecimal.ZERO;
  }

  /**
   * Add a section to this report.
   *
   * @param section the section to add
   */
  public void addSection(ReportSectionDTO section) {
    if (this.sections == null) {
      this.sections = new ArrayList<>();
    }
    this.sections.add(section);
  }

  /**
   * Calculate net income from revenue and expenses.
   * Should be called after all sections are populated.
   */
  public void calculateNetIncome() {
    this.netIncome = this.totalRevenue.subtract(this.totalExpenses);
  }

  /**
   * Validate balance sheet equation: Assets = Liabilities + Equity
   *
   * @return true if the equation holds (within rounding tolerance)
   */
  public boolean validateBalanceSheetEquation() {
    if (this.reportType != ReportType.BALANCE_SHEET) {
      return true; // Not applicable for Income Statement
    }
    BigDecimal leftSide = this.totalAssets;
    BigDecimal rightSide = this.totalLiabilities.add(this.totalEquity);
    return leftSide.compareTo(rightSide) == 0;
  }
}
