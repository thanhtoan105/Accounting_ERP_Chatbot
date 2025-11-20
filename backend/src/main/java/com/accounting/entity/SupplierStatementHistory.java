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
 * SupplierStatementHistory entity for tracking all generated and sent supplier statements.
 * Maintains complete history with metadata, hash, and delivery information for audit compliance.
 */
@Entity
@Table(name = "supplier_statement_history")
public class SupplierStatementHistory implements CompanyScopedEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @NotNull
  @Column(name = "company_id", nullable = false)
  private Long companyId;

  @NotNull
  @Column(name = "supplier_id", nullable = false)
  private Long supplierId;

  @NotNull
  @Enumerated(EnumType.STRING)
  @Column(name = "statement_type", nullable = false, length = 20)
  private StatementType statementType;

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

  @Column(name = "sent_date")
  private Instant sentDate;

  @Column(name = "sent_to", columnDefinition = "TEXT")
  private String sentTo;

  @NotNull
  @Column(name = "view_count", nullable = false)
  private Integer viewCount = 0;

  @NotNull
  @Column(name = "download_count", nullable = false)
  private Integer downloadCount = 0;

  @NotNull
  @Column(name = "start_date", nullable = false)
  private LocalDate startDate;

  @NotNull
  @Column(name = "end_date", nullable = false)
  private LocalDate endDate;

  // Relationships
  @ManyToOne
  @JoinColumn(name = "company_id", insertable = false, updatable = false)
  private Company company;

  @ManyToOne
  @JoinColumn(name = "supplier_id", insertable = false, updatable = false)
  private Supplier supplier;

  @ManyToOne
  @JoinColumn(name = "generated_by", insertable = false, updatable = false)
  private User generatedByUser;

  @PrePersist
  protected void onCreate() {
    if (generationDate == null) {
      generationDate = Instant.now();
    }
  }

  // Enum for statement type
  public enum StatementType {
    SUMMARY,
    DETAILED
  }

  // Enum for export format
  public enum ExportFormat {
    PDF,
    EXCEL
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

  public Long getSupplierId() {
    return supplierId;
  }

  public void setSupplierId(Long supplierId) {
    this.supplierId = supplierId;
  }

  public StatementType getStatementType() {
    return statementType;
  }

  public void setStatementType(StatementType statementType) {
    this.statementType = statementType;
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

  public Instant getSentDate() {
    return sentDate;
  }

  public void setSentDate(Instant sentDate) {
    this.sentDate = sentDate;
  }

  public String getSentTo() {
    return sentTo;
  }

  public void setSentTo(String sentTo) {
    this.sentTo = sentTo;
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

  public Company getCompany() {
    return company;
  }

  public void setCompany(Company company) {
    this.company = company;
  }

  public Supplier getSupplier() {
    return supplier;
  }

  public void setSupplier(Supplier supplier) {
    this.supplier = supplier;
  }

  public User getGeneratedByUser() {
    return generatedByUser;
  }

  public void setGeneratedByUser(User generatedByUser) {
    this.generatedByUser = generatedByUser;
  }

  public void incrementViewCount() {
    this.viewCount++;
  }

  public void incrementDownloadCount() {
    this.downloadCount++;
  }
}

