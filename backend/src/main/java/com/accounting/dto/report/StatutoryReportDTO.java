package com.accounting.dto.report;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * DTO representing a complete statutory financial report (B01, B02, B03, F01).
 * Enhanced for TT200 compliance with comparison period, variance analysis, and drill-down support.
 */
public class StatutoryReportDTO {

  // Report metadata
  private UUID reportId;
  private String reportType;          // 'B01', 'B02', 'B03', 'F01'
  private String reportName;          // e.g., "Bảng cân đối kế toán"
  private String reportNameEnglish;   // e.g., "Balance Sheet"

  // Period information
  private UUID periodId;
  private String periodName;
  private LocalDate periodStartDate;
  private LocalDate periodEndDate;

  // Comparison period (optional)
  private UUID comparisonPeriodId;
  private String comparisonPeriodName;
  private LocalDate comparisonPeriodStartDate;
  private LocalDate comparisonPeriodEndDate;

  // Company information
  private Long companyId;
  private String companyName;
  private String companyTaxCode;
  private String companyAddress;

  // Report data
  private List<StatutoryReportLineDTO> lines;
  private Integer mappingVersion;

  // Totals (for quick access)
  private BigDecimal totalAssets;         // B01
  private BigDecimal totalLiabilities;    // B01
  private BigDecimal totalEquity;         // B01
  private BigDecimal totalRevenue;        // B02
  private BigDecimal netIncome;           // B02
  private BigDecimal netCashFlow;         // B03

  // Status flags
  private boolean isDraft;                // TRUE if period is open
  private boolean isBalanced;             // TRUE if Assets = Liabilities + Equity

  // Generation metadata
  private Instant generatedAt;
  private String generatedByName;
  private String snapshotHash;            // For integrity verification

  // Validation warnings (if any)
  private List<String> validationWarnings;

  public StatutoryReportDTO() {
    this.lines = new ArrayList<>();
    this.validationWarnings = new ArrayList<>();
    this.totalAssets = BigDecimal.ZERO;
    this.totalLiabilities = BigDecimal.ZERO;
    this.totalEquity = BigDecimal.ZERO;
    this.totalRevenue = BigDecimal.ZERO;
    this.netIncome = BigDecimal.ZERO;
    this.netCashFlow = BigDecimal.ZERO;
    this.isBalanced = true;
  }

  // Getters and Setters

  public UUID getReportId() {
    return reportId;
  }

  public void setReportId(UUID reportId) {
    this.reportId = reportId;
  }

  public String getReportType() {
    return reportType;
  }

  public void setReportType(String reportType) {
    this.reportType = reportType;
  }

  public String getReportName() {
    return reportName;
  }

  public void setReportName(String reportName) {
    this.reportName = reportName;
  }

  public String getReportNameEnglish() {
    return reportNameEnglish;
  }

  public void setReportNameEnglish(String reportNameEnglish) {
    this.reportNameEnglish = reportNameEnglish;
  }

  public UUID getPeriodId() {
    return periodId;
  }

  public void setPeriodId(UUID periodId) {
    this.periodId = periodId;
  }

  public String getPeriodName() {
    return periodName;
  }

  public void setPeriodName(String periodName) {
    this.periodName = periodName;
  }

  public LocalDate getPeriodStartDate() {
    return periodStartDate;
  }

  public void setPeriodStartDate(LocalDate periodStartDate) {
    this.periodStartDate = periodStartDate;
  }

  public LocalDate getPeriodEndDate() {
    return periodEndDate;
  }

  public void setPeriodEndDate(LocalDate periodEndDate) {
    this.periodEndDate = periodEndDate;
  }

  public UUID getComparisonPeriodId() {
    return comparisonPeriodId;
  }

  public void setComparisonPeriodId(UUID comparisonPeriodId) {
    this.comparisonPeriodId = comparisonPeriodId;
  }

  public String getComparisonPeriodName() {
    return comparisonPeriodName;
  }

  public void setComparisonPeriodName(String comparisonPeriodName) {
    this.comparisonPeriodName = comparisonPeriodName;
  }

  public LocalDate getComparisonPeriodStartDate() {
    return comparisonPeriodStartDate;
  }

  public void setComparisonPeriodStartDate(LocalDate comparisonPeriodStartDate) {
    this.comparisonPeriodStartDate = comparisonPeriodStartDate;
  }

  public LocalDate getComparisonPeriodEndDate() {
    return comparisonPeriodEndDate;
  }

  public void setComparisonPeriodEndDate(LocalDate comparisonPeriodEndDate) {
    this.comparisonPeriodEndDate = comparisonPeriodEndDate;
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

  public String getCompanyTaxCode() {
    return companyTaxCode;
  }

  public void setCompanyTaxCode(String companyTaxCode) {
    this.companyTaxCode = companyTaxCode;
  }

  public String getCompanyAddress() {
    return companyAddress;
  }

  public void setCompanyAddress(String companyAddress) {
    this.companyAddress = companyAddress;
  }

  public List<StatutoryReportLineDTO> getLines() {
    return lines;
  }

  public void setLines(List<StatutoryReportLineDTO> lines) {
    this.lines = lines != null ? lines : new ArrayList<>();
  }

  public Integer getMappingVersion() {
    return mappingVersion;
  }

  public void setMappingVersion(Integer mappingVersion) {
    this.mappingVersion = mappingVersion;
  }

  public BigDecimal getTotalAssets() {
    return totalAssets;
  }

  public void setTotalAssets(BigDecimal totalAssets) {
    this.totalAssets = totalAssets;
  }

  public BigDecimal getTotalLiabilities() {
    return totalLiabilities;
  }

  public void setTotalLiabilities(BigDecimal totalLiabilities) {
    this.totalLiabilities = totalLiabilities;
  }

  public BigDecimal getTotalEquity() {
    return totalEquity;
  }

  public void setTotalEquity(BigDecimal totalEquity) {
    this.totalEquity = totalEquity;
  }

  public BigDecimal getTotalRevenue() {
    return totalRevenue;
  }

  public void setTotalRevenue(BigDecimal totalRevenue) {
    this.totalRevenue = totalRevenue;
  }

  public BigDecimal getNetIncome() {
    return netIncome;
  }

  public void setNetIncome(BigDecimal netIncome) {
    this.netIncome = netIncome;
  }

  public BigDecimal getNetCashFlow() {
    return netCashFlow;
  }

  public void setNetCashFlow(BigDecimal netCashFlow) {
    this.netCashFlow = netCashFlow;
  }

  public boolean isDraft() {
    return isDraft;
  }

  public void setDraft(boolean draft) {
    isDraft = draft;
  }

  public boolean isBalanced() {
    return isBalanced;
  }

  public void setBalanced(boolean balanced) {
    isBalanced = balanced;
  }

  public Instant getGeneratedAt() {
    return generatedAt;
  }

  public void setGeneratedAt(Instant generatedAt) {
    this.generatedAt = generatedAt;
  }

  public String getGeneratedByName() {
    return generatedByName;
  }

  public void setGeneratedByName(String generatedByName) {
    this.generatedByName = generatedByName;
  }

  public String getSnapshotHash() {
    return snapshotHash;
  }

  public void setSnapshotHash(String snapshotHash) {
    this.snapshotHash = snapshotHash;
  }

  public List<String> getValidationWarnings() {
    return validationWarnings;
  }

  public void setValidationWarnings(List<String> validationWarnings) {
    this.validationWarnings = validationWarnings != null ? validationWarnings : new ArrayList<>();
  }

  /**
   * Add a validation warning.
   *
   * @param warning warning message
   */
  public void addValidationWarning(String warning) {
    if (this.validationWarnings == null) {
      this.validationWarnings = new ArrayList<>();
    }
    this.validationWarnings.add(warning);
  }

  /**
   * Add a line to the report.
   *
   * @param line the line to add
   */
  public void addLine(StatutoryReportLineDTO line) {
    if (this.lines == null) {
      this.lines = new ArrayList<>();
    }
    this.lines.add(line);
  }

  /**
   * Check if this report has comparison period data.
   *
   * @return true if comparison period is set
   */
  public boolean hasComparison() {
    return comparisonPeriodId != null;
  }
}
