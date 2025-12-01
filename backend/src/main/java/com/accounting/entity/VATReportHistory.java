package com.accounting.entity;

import com.accounting.repository.CompanyScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * VATReportHistory entity for tracking all generated VAT reports.
 * Maintains complete history with metadata, hash, and format information for audit compliance.
 */
@Entity
@Table(name = "vat_report_history")
public class VATReportHistory implements CompanyScopedEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @NotNull
  @Column(name = "company_id", nullable = false)
  private Long companyId;

  @NotNull
  @Enumerated(EnumType.STRING)
  @Column(name = "report_type", nullable = false, length = 20)
  private ReportType reportType;

  @Column(name = "period_id")
  private UUID periodId;

  @Column(name = "supplier_id")
  private Long supplierId; // Optional: for INPUT_VAT reports, null means all suppliers

  @Column(name = "customer_id")
  private Long customerId; // Optional: for OUTPUT_VAT reports, null means all customers

  @Size(max = 50)
  @Column(name = "vat_class", length = 50)
  private String vatClass; // Optional: filter by VAT class

  @NotNull
  @Column(name = "generation_date", nullable = false)
  private Instant generationDate;

  @NotNull
  @Column(name = "generated_by", nullable = false)
  private Long generatedBy;

  @NotNull
  @Enumerated(EnumType.STRING)
  @Column(name = "format", nullable = false, length = 10)
  private ExportFormat format;

  @Size(max = 500)
  @Column(name = "file_path", length = 500)
  private String filePath;

  @NotBlank
  @Size(max = 64)
  @Column(name = "hash", nullable = false, length = 64)
  private String hash;

  @NotNull
  @Column(name = "start_date", nullable = false)
  private LocalDate startDate;

  @NotNull
  @Column(name = "end_date", nullable = false)
  private LocalDate endDate;

  @NotNull
  @Column(name = "view_count", nullable = false)
  private Integer viewCount = 0;

  @NotNull
  @Column(name = "download_count", nullable = false)
  private Integer downloadCount = 0;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  // Relationships
  @ManyToOne
  @JoinColumn(name = "company_id", insertable = false, updatable = false)
  private Company company;

  @ManyToOne
  @JoinColumn(name = "supplier_id", insertable = false, updatable = false)
  private Supplier supplier;

  @ManyToOne
  @JoinColumn(name = "customer_id", insertable = false, updatable = false)
  private Customer customer;

  @PrePersist
  protected void onCreate() {
    createdAt = Instant.now();
    updatedAt = Instant.now();
    if (generationDate == null) {
      generationDate = Instant.now();
    }
  }

  @PreUpdate
  protected void onUpdate() {
    updatedAt = Instant.now();
  }

  public enum ReportType {
    INPUT_VAT("Input VAT Report"),
    OUTPUT_VAT("Output VAT Report");

    private final String displayName;

    ReportType(String displayName) {
      this.displayName = displayName;
    }

    public String getDisplayName() {
      return displayName;
    }
  }

  public enum ExportFormat {
    PDF("PDF"),
    EXCEL("Excel");

    private final String displayName;

    ExportFormat(String displayName) {
      this.displayName = displayName;
    }

    public String getDisplayName() {
      return displayName;
    }
  }

  // Getters and setters
  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  @Override
  public Long getCompanyId() {
    return companyId;
  }

  public void setCompanyId(Long companyId) {
    this.companyId = companyId;
  }

  public ReportType getReportType() {
    return reportType;
  }

  public void setReportType(ReportType reportType) {
    this.reportType = reportType;
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

  public Long getCustomerId() {
    return customerId;
  }

  public void setCustomerId(Long customerId) {
    this.customerId = customerId;
  }

  public String getVatClass() {
    return vatClass;
  }

  public void setVatClass(String vatClass) {
    this.vatClass = vatClass;
  }

  public Instant getGenerationDate() {
    return generationDate;
  }

  public void setGenerationDate(Instant generationDate) {
    this.generationDate = generationDate;
  }

  public Long getGeneratedBy() {
    return generatedBy;
  }

  public void setGeneratedBy(Long generatedBy) {
    this.generatedBy = generatedBy;
  }

  public ExportFormat getFormat() {
    return format;
  }

  public void setFormat(ExportFormat format) {
    this.format = format;
  }

  public String getFilePath() {
    return filePath;
  }

  public void setFilePath(String filePath) {
    this.filePath = filePath;
  }

  public String getHash() {
    return hash;
  }

  public void setHash(String hash) {
    this.hash = hash;
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

  public Integer getViewCount() {
    return viewCount;
  }

  public void setViewCount(Integer viewCount) {
    this.viewCount = viewCount;
  }

  public Integer getDownloadCount() {
    return downloadCount;
  }

  public void setDownloadCount(Integer downloadCount) {
    this.downloadCount = downloadCount;
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

  public Company getCompany() {
    return company;
  }

  public Supplier getSupplier() {
    return supplier;
  }

  public Customer getCustomer() {
    return customer;
  }
}

