package com.accounting.dto;

import java.time.Instant;

/**
 * DTO for CompanySettings responses. Used in GET /api/v1/company-settings
 * endpoint.
 */
public class CompanySettingsDto {

  private Long id;
  private Long companyId;

  // General section
  private String legalName;
  private String shortName;
  private String registrationNumber;
  private Integer defaultFiscalYearStartMonth;
  private String timezone;

  // Localization section
  private String defaultCurrency;
  private String currencyFormat;
  private String thousandSeparator;
  private String decimalSeparator;
  private String dateFormat;
  private String language;

  // Tax & Compliance section
  private String vatRegistrationNumber;
  private String vatRatePresets; // JSON string
  private String invoiceRoundingMode;
  private String taxRoundingMode;
  private Boolean eInvoiceEnabled;
  private Integer auditRetentionPeriodDays;

  // Approval Workflow section
  private java.math.BigDecimal approvalThresholdAmount;
  private java.math.BigDecimal salesInvoiceApprovalThresholdAmount;

  // Numbering section
  private String numberingConfig; // JSON string

  // Integrations section
  private Boolean bankReconciliationEnabled;
  private String exportFormatDefault;

  private Instant createdAt;
  private Instant updatedAt;

  public CompanySettingsDto() {
  }

  // Getters and Setters
  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public Long getCompanyId() {
    return companyId;
  }

  public void setCompanyId(Long companyId) {
    this.companyId = companyId;
  }

  public String getLegalName() {
    return legalName;
  }

  public void setLegalName(String legalName) {
    this.legalName = legalName;
  }

  public String getShortName() {
    return shortName;
  }

  public void setShortName(String shortName) {
    this.shortName = shortName;
  }

  public String getRegistrationNumber() {
    return registrationNumber;
  }

  public void setRegistrationNumber(String registrationNumber) {
    this.registrationNumber = registrationNumber;
  }

  public Integer getDefaultFiscalYearStartMonth() {
    return defaultFiscalYearStartMonth;
  }

  public void setDefaultFiscalYearStartMonth(Integer defaultFiscalYearStartMonth) {
    this.defaultFiscalYearStartMonth = defaultFiscalYearStartMonth;
  }

  public String getTimezone() {
    return timezone;
  }

  public void setTimezone(String timezone) {
    this.timezone = timezone;
  }

  public String getDefaultCurrency() {
    return defaultCurrency;
  }

  public void setDefaultCurrency(String defaultCurrency) {
    this.defaultCurrency = defaultCurrency;
  }

  public String getCurrencyFormat() {
    return currencyFormat;
  }

  public void setCurrencyFormat(String currencyFormat) {
    this.currencyFormat = currencyFormat;
  }

  public String getThousandSeparator() {
    return thousandSeparator;
  }

  public void setThousandSeparator(String thousandSeparator) {
    this.thousandSeparator = thousandSeparator;
  }

  public String getDecimalSeparator() {
    return decimalSeparator;
  }

  public void setDecimalSeparator(String decimalSeparator) {
    this.decimalSeparator = decimalSeparator;
  }

  public String getDateFormat() {
    return dateFormat;
  }

  public void setDateFormat(String dateFormat) {
    this.dateFormat = dateFormat;
  }

  public String getLanguage() {
    return language;
  }

  public void setLanguage(String language) {
    this.language = language;
  }

  public String getVatRegistrationNumber() {
    return vatRegistrationNumber;
  }

  public void setVatRegistrationNumber(String vatRegistrationNumber) {
    this.vatRegistrationNumber = vatRegistrationNumber;
  }

  public String getVatRatePresets() {
    return vatRatePresets;
  }

  public void setVatRatePresets(String vatRatePresets) {
    this.vatRatePresets = vatRatePresets;
  }

  public String getInvoiceRoundingMode() {
    return invoiceRoundingMode;
  }

  public void setInvoiceRoundingMode(String invoiceRoundingMode) {
    this.invoiceRoundingMode = invoiceRoundingMode;
  }

  public String getTaxRoundingMode() {
    return taxRoundingMode;
  }

  public void setTaxRoundingMode(String taxRoundingMode) {
    this.taxRoundingMode = taxRoundingMode;
  }

  public Boolean getEInvoiceEnabled() {
    return eInvoiceEnabled;
  }

  public void setEInvoiceEnabled(Boolean eInvoiceEnabled) {
    this.eInvoiceEnabled = eInvoiceEnabled;
  }

  public Integer getAuditRetentionPeriodDays() {
    return auditRetentionPeriodDays;
  }

  public void setAuditRetentionPeriodDays(Integer auditRetentionPeriodDays) {
    this.auditRetentionPeriodDays = auditRetentionPeriodDays;
  }

  public java.math.BigDecimal getApprovalThresholdAmount() {
    return approvalThresholdAmount;
  }

  public void setApprovalThresholdAmount(java.math.BigDecimal approvalThresholdAmount) {
    this.approvalThresholdAmount = approvalThresholdAmount;
  }

  public java.math.BigDecimal getSalesInvoiceApprovalThresholdAmount() {
    return salesInvoiceApprovalThresholdAmount;
  }

  public void setSalesInvoiceApprovalThresholdAmount(
      java.math.BigDecimal salesInvoiceApprovalThresholdAmount) {
    this.salesInvoiceApprovalThresholdAmount = salesInvoiceApprovalThresholdAmount;
  }

  public String getNumberingConfig() {
    return numberingConfig;
  }

  public void setNumberingConfig(String numberingConfig) {
    this.numberingConfig = numberingConfig;
  }

  public Boolean getBankReconciliationEnabled() {
    return bankReconciliationEnabled;
  }

  public void setBankReconciliationEnabled(Boolean bankReconciliationEnabled) {
    this.bankReconciliationEnabled = bankReconciliationEnabled;
  }

  public String getExportFormatDefault() {
    return exportFormatDefault;
  }

  public void setExportFormatDefault(String exportFormatDefault) {
    this.exportFormatDefault = exportFormatDefault;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }
}
