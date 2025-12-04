package com.accounting.entity.report;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Entity representing an immutable snapshot of a generated statutory report.
 * Used for reproducibility, legal compliance, and audit trail.
 *
 * Once a snapshot is marked as final (is_final=true), it cannot be modified or deleted.
 * Legal hold can be applied to preserve snapshots for audit/investigation.
 */
@Entity
@Table(name = "report_snapshots")
public class ReportSnapshot {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "company_id", nullable = false)
  private Long companyId;

  @Column(name = "report_type", nullable = false, length = 20)
  private String reportType; // 'B01', 'B02', 'B03', 'F01'

  @Column(name = "period_id", nullable = false)
  private UUID periodId;

  @Column(name = "comparison_period_id")
  private UUID comparisonPeriodId;

  @Column(name = "mapping_version", nullable = false)
  private Integer mappingVersion; // Which mapping version was used

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "parameters", nullable = false, columnDefinition = "jsonb")
  private JsonNode parameters; // Additional parameters used

  @Column(name = "data_hash", nullable = false, length = 64)
  private String dataHash; // SHA-256 hash of snapshot_data

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "snapshot_data", nullable = false, columnDefinition = "jsonb")
  private JsonNode snapshotData; // Full report data (lines, totals, etc.)

  @Column(name = "generated_by", nullable = false)
  private Long generatedBy;

  @Column(name = "generated_at")
  private Instant generatedAt;

  @Column(name = "is_draft")
  private Boolean isDraft = true; // TRUE if period was open at generation

  @Column(name = "is_final")
  private Boolean isFinal = false; // TRUE = cannot be regenerated

  @Column(name = "legal_hold")
  private Boolean legalHold = false;

  @Column(name = "legal_hold_reason", columnDefinition = "TEXT")
  private String legalHoldReason;

  @Column(name = "legal_hold_by")
  private Long legalHoldBy;

  @Column(name = "legal_hold_at")
  private Instant legalHoldAt;

  @Column(name = "deleted_at")
  private Instant deletedAt;

  @Column(name = "deleted_by")
  private Long deletedBy;

  @PrePersist
  public void prePersist() {
    if (generatedAt == null) {
      generatedAt = Instant.now();
    }
  }

  // Getters and Setters

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public Long getCompanyId() {
    return companyId;
  }

  public void setCompanyId(Long companyId) {
    this.companyId = companyId;
  }

  public String getReportType() {
    return reportType;
  }

  public void setReportType(String reportType) {
    this.reportType = reportType;
  }

  public UUID getPeriodId() {
    return periodId;
  }

  public void setPeriodId(UUID periodId) {
    this.periodId = periodId;
  }

  public UUID getComparisonPeriodId() {
    return comparisonPeriodId;
  }

  public void setComparisonPeriodId(UUID comparisonPeriodId) {
    this.comparisonPeriodId = comparisonPeriodId;
  }

  public Integer getMappingVersion() {
    return mappingVersion;
  }

  public void setMappingVersion(Integer mappingVersion) {
    this.mappingVersion = mappingVersion;
  }

  public JsonNode getParameters() {
    return parameters;
  }

  public void setParameters(JsonNode parameters) {
    this.parameters = parameters;
  }

  public String getDataHash() {
    return dataHash;
  }

  public void setDataHash(String dataHash) {
    this.dataHash = dataHash;
  }

  public JsonNode getSnapshotData() {
    return snapshotData;
  }

  public void setSnapshotData(JsonNode snapshotData) {
    this.snapshotData = snapshotData;
  }

  public Long getGeneratedBy() {
    return generatedBy;
  }

  public void setGeneratedBy(Long generatedBy) {
    this.generatedBy = generatedBy;
  }

  public Instant getGeneratedAt() {
    return generatedAt;
  }

  public void setGeneratedAt(Instant generatedAt) {
    this.generatedAt = generatedAt;
  }

  public Boolean getIsDraft() {
    return isDraft;
  }

  public void setIsDraft(Boolean isDraft) {
    this.isDraft = isDraft;
  }

  public Boolean getIsFinal() {
    return isFinal;
  }

  public void setIsFinal(Boolean isFinal) {
    this.isFinal = isFinal;
  }

  public Boolean getLegalHold() {
    return legalHold;
  }

  public void setLegalHold(Boolean legalHold) {
    this.legalHold = legalHold;
  }

  public String getLegalHoldReason() {
    return legalHoldReason;
  }

  public void setLegalHoldReason(String legalHoldReason) {
    this.legalHoldReason = legalHoldReason;
  }

  public Long getLegalHoldBy() {
    return legalHoldBy;
  }

  public void setLegalHoldBy(Long legalHoldBy) {
    this.legalHoldBy = legalHoldBy;
  }

  public Instant getLegalHoldAt() {
    return legalHoldAt;
  }

  public void setLegalHoldAt(Instant legalHoldAt) {
    this.legalHoldAt = legalHoldAt;
  }

  public Instant getDeletedAt() {
    return deletedAt;
  }

  public void setDeletedAt(Instant deletedAt) {
    this.deletedAt = deletedAt;
  }

  public Long getDeletedBy() {
    return deletedBy;
  }

  public void setDeletedBy(Long deletedBy) {
    this.deletedBy = deletedBy;
  }

  /**
   * Check if this snapshot can be modified or deleted.
   *
   * @return true if the snapshot is protected from modification
   */
  public boolean isProtected() {
    return Boolean.TRUE.equals(isFinal) || Boolean.TRUE.equals(legalHold);
  }

  /**
   * Check if this snapshot is soft deleted.
   *
   * @return true if the snapshot is deleted
   */
  public boolean isDeleted() {
    return deletedAt != null;
  }

  /**
   * Apply legal hold to this snapshot.
   *
   * @param reason the reason for the legal hold
   * @param userId the user applying the hold
   */
  public void applyLegalHold(String reason, Long userId) {
    this.legalHold = true;
    this.legalHoldReason = reason;
    this.legalHoldBy = userId;
    this.legalHoldAt = Instant.now();
  }

  /**
   * Remove legal hold from this snapshot.
   * Note: Only removes if not marked as final.
   */
  public void removeLegalHold() {
    if (!Boolean.TRUE.equals(isFinal)) {
      this.legalHold = false;
      this.legalHoldReason = null;
      this.legalHoldBy = null;
      this.legalHoldAt = null;
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ReportSnapshot that = (ReportSnapshot) o;
    return Objects.equals(id, that.id);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }

  @Override
  public String toString() {
    return "ReportSnapshot{" +
        "id=" + id +
        ", companyId=" + companyId +
        ", reportType='" + reportType + '\'' +
        ", periodId=" + periodId +
        ", mappingVersion=" + mappingVersion +
        ", isDraft=" + isDraft +
        ", isFinal=" + isFinal +
        ", generatedAt=" + generatedAt +
        '}';
  }
}
