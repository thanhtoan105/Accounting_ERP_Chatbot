package com.accounting.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * DTO for updating advanced CompanySettings. Used in PUT
 * /api/v1/company-settings endpoint.
 * All fields are optional to support partial updates.
 * Includes optimistic locking via updatedAt field.
 */
public class UpdateCompanySettingsRequest {

  // General section
  @Size(max = 255)
  private String legalName;

  @Size(max = 100)
  private String shortName;

  @Size(max = 50)
  private String registrationNumber;

  @Min(1)
  @Max(12)
  private Integer defaultFiscalYearStartMonth; // 1-12

  @Size(max = 50)
  private String timezone;

  // Localization section
  @Size(max = 3)
  @Pattern(regexp = "^[A-Z]{3}$", message = "Currency code must be 3 uppercase letters")
  private String defaultCurrency;

  @Size(max = 20)
  private String currencyFormat;

  @Size(max = 1)
  private String thousandSeparator;

  @Size(max = 1)
  private String decimalSeparator;

  @Size(max = 20)
  private String dateFormat;

  @Size(max = 10)
  private String language;

  // Tax & Compliance section
  @Size(max = 20)
  @Pattern(regexp = "^\\d{10}$", message = "VAT registration number must be 10 digits")
  private String vatRegistrationNumber;

  private String vatRatePresets; // JSON string

  @Size(max = 20)
  private String invoiceRoundingMode;

  @Size(max = 20)
  private String taxRoundingMode;

  private Boolean eInvoiceEnabled;

  @Min(0)
  private Integer auditRetentionPeriodDays;

  // Approval Workflow section
  @Min(0)
  private java.math.BigDecimal approvalThresholdAmount;

  // Numbering section
  private String numberingConfig; // JSON string

  // Integrations section
  private Boolean bankReconciliationEnabled;

  @Size(max = 10)
  private String exportFormatDefault;

  // Optimistic locking: client sends updatedAt to detect conflicts
  private java.time.Instant updatedAt;

  // Getters and Setters
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

  public java.time.Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(java.time.Instant updatedAt) {
    this.updatedAt = updatedAt;
  }
}
