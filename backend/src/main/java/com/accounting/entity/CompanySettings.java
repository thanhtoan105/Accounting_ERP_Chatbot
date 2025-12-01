package com.accounting.entity;

import com.accounting.repository.CompanyScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * CompanySettings entity for advanced company configuration.
 * Stores tax, currency, localization, numbering, and compliance settings.
 * Implements CompanyScopedEntity for multi-tenancy.
 */
@Entity
@Table(name = "company_settings")
public class CompanySettings implements CompanyScopedEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @NotNull
  @Column(name = "company_id", nullable = false, unique = true)
  private Long companyId;

  // General section
  @Size(max = 255)
  @Column(name = "legal_name", length = 255)
  private String legalName;

  @Size(max = 100)
  @Column(name = "short_name", length = 100)
  private String shortName;

  @Size(max = 50)
  @Column(name = "registration_number", length = 50)
  private String registrationNumber;

  @Min(1)
  @Column(name = "default_fiscal_year_start_month")
  private Integer defaultFiscalYearStartMonth; // 1-12

  @Size(max = 50)
  @Column(name = "timezone", length = 50)
  private String timezone; // e.g., "Asia/Ho_Chi_Minh"

  // Localization section
  @Size(max = 3)
  @Pattern(regexp = "^[A-Z]{3}$", message = "Currency code must be 3 uppercase letters")
  @Column(name = "default_currency", length = 3)
  private String defaultCurrency; // e.g., "VND"

  @Size(max = 20)
  @Column(name = "currency_format", length = 20)
  private String currencyFormat; // e.g., "#,##0.00"

  @Size(max = 1)
  @Column(name = "thousand_separator", length = 1)
  private String thousandSeparator; // e.g., ","

  @Size(max = 1)
  @Column(name = "decimal_separator", length = 1)
  private String decimalSeparator; // e.g., "."

  @Size(max = 20)
  @Column(name = "date_format", length = 20)
  private String dateFormat; // e.g., "ISO", "VN", "DD/MM/YYYY"

  @Size(max = 10)
  @Column(name = "language", length = 10)
  private String language; // e.g., "vi", "en"

  // Tax & Compliance section
  @Size(max = 20)
  @Pattern(regexp = "^\\d{10}$", message = "VAT registration number must be 10 digits")
  @Column(name = "vat_registration_number", length = 20)
  private String vatRegistrationNumber;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "vat_rate_presets", columnDefinition = "JSONB")
  private String vatRatePresets; // JSON array of rates, e.g., [0, 5, 10]

  @Size(max = 20)
  @Column(name = "invoice_rounding_mode", length = 20)
  private String invoiceRoundingMode; // e.g., "HALF_UP", "HALF_DOWN", "UP", "DOWN"

  @Size(max = 20)
  @Column(name = "tax_rounding_mode", length = 20)
  private String taxRoundingMode;

  @Column(name = "e_invoice_enabled", nullable = false)
  private Boolean eInvoiceEnabled = false; // Placeholder for future

  @Min(0)
  @Column(name = "audit_retention_period_days")
  private Integer auditRetentionPeriodDays; // Days to retain audit logs

  // Approval Workflow section
  @Min(0)
  @Column(name = "approval_threshold_amount", precision = 19, scale = 2)
  private java.math.BigDecimal approvalThresholdAmount; // Default 20,000,000 VND

  @Min(0)
  @Column(name = "sales_invoice_approval_threshold_amount", precision = 19, scale = 2)
  private java.math.BigDecimal salesInvoiceApprovalThresholdAmount; // Default 100,000,000 VND

  // Numbering section (stored as JSON)
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "numbering_config", columnDefinition = "JSONB")
  private String numberingConfig; // JSON: { "voucher": { "prefix": "VC", "sequence": 1 }, ... }

  // Integrations section
  @Column(name = "bank_reconciliation_enabled", nullable = false)
  private Boolean bankReconciliationEnabled = false; // Placeholder

  @Size(max = 10)
  @Column(name = "export_format_default", length = 10)
  private String exportFormatDefault; // e.g., "EXCEL", "CSV"

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  protected void onCreate() {
    createdAt = Instant.now();
    updatedAt = Instant.now();
  }

  @PreUpdate
  protected void onUpdate() {
    updatedAt = Instant.now();
  }

  // Getters and Setters
  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  @Override
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
