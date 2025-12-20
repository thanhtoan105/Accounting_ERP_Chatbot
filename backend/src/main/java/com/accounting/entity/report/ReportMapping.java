package com.accounting.entity.report;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/**
 * Entity representing a TT200 report line-to-account mapping.
 * Supports versioning for rollback capability and audit trail.
 *
 * Each line code (e.g., "100", "110") maps to one or more GL account patterns
 * to aggregate values for statutory reports (B01, B02, B03, F01).
 */
@Entity
@Table(name = "report_mappings")
public class ReportMapping {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "company_id", nullable = false)
  private Long companyId;

  @Column(name = "report_type", nullable = false, length = 20)
  private String reportType; // 'B01', 'B02', 'B03', 'F01'

  @Column(name = "line_code", nullable = false, length = 20)
  private String lineCode; // TT200 line code (e.g., '100', '110')

  @Column(name = "line_name", nullable = false, length = 255)
  private String lineName; // Vietnamese line name

  @Column(name = "line_name_english", length = 255)
  private String lineNameEnglish; // English translation for i18n

  @Column(name = "account_pattern", nullable = false, columnDefinition = "TEXT")
  private String accountPattern; // Account codes/ranges (e.g., '111*,112*')

  @Column(name = "operator", length = 10)
  private String operator = "SUM"; // 'SUM', 'DIFF', 'ABS', 'CALC'

  @Column(name = "sign_modifier")
  private Integer signModifier = 1; // 1 or -1 for balance direction

  @Column(name = "display_order", nullable = false)
  private Integer displayOrder;

  @Column(name = "parent_line_code", length = 20)
  private String parentLineCode; // For hierarchical structure

  @Column(name = "level")
  private Integer level = 1; // Indentation level (1=main, 2=sub, 3=detail)

  @Column(name = "is_calculated")
  private Boolean isCalculated = false; // TRUE for lines computed from other lines

  @Column(name = "formula", columnDefinition = "TEXT")
  private String formula; // Formula for calculated lines (e.g., "01-11")

  @Column(name = "version")
  private Integer version = 1;

  @Column(name = "is_current")
  private Boolean isCurrent = true; // Quick lookup for current version

  @Column(name = "created_at")
  private Instant createdAt;

  @Column(name = "updated_at")
  private Instant updatedAt;

  @Column(name = "created_by")
  private Long createdBy;

  @Column(name = "change_reason", columnDefinition = "TEXT")
  private String changeReason; // Required for audit when updating

  @PrePersist
  public void prePersist() {
    if (createdAt == null) {
      createdAt = Instant.now();
    }
    updatedAt = Instant.now();
  }

  @PreUpdate
  public void preUpdate() {
    updatedAt = Instant.now();
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

  public String getLineCode() {
    return lineCode;
  }

  public void setLineCode(String lineCode) {
    this.lineCode = lineCode;
  }

  public String getLineName() {
    return lineName;
  }

  public void setLineName(String lineName) {
    this.lineName = lineName;
  }

  public String getLineNameEnglish() {
    return lineNameEnglish;
  }

  public void setLineNameEnglish(String lineNameEnglish) {
    this.lineNameEnglish = lineNameEnglish;
  }

  public String getAccountPattern() {
    return accountPattern;
  }

  public void setAccountPattern(String accountPattern) {
    this.accountPattern = accountPattern;
  }

  public String getOperator() {
    return operator;
  }

  public void setOperator(String operator) {
    this.operator = operator;
  }

  public Integer getSignModifier() {
    return signModifier;
  }

  public void setSignModifier(Integer signModifier) {
    this.signModifier = signModifier;
  }

  public Integer getDisplayOrder() {
    return displayOrder;
  }

  public void setDisplayOrder(Integer displayOrder) {
    this.displayOrder = displayOrder;
  }

  public String getParentLineCode() {
    return parentLineCode;
  }

  public void setParentLineCode(String parentLineCode) {
    this.parentLineCode = parentLineCode;
  }

  public Integer getLevel() {
    return level;
  }

  public void setLevel(Integer level) {
    this.level = level;
  }

  public Boolean getIsCalculated() {
    return isCalculated;
  }

  public void setIsCalculated(Boolean isCalculated) {
    this.isCalculated = isCalculated;
  }

  public String getFormula() {
    return formula;
  }

  public void setFormula(String formula) {
    this.formula = formula;
  }

  public Integer getVersion() {
    return version;
  }

  public void setVersion(Integer version) {
    this.version = version;
  }

  public Boolean getIsCurrent() {
    return isCurrent;
  }

  public void setIsCurrent(Boolean isCurrent) {
    this.isCurrent = isCurrent;
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

  public Long getCreatedBy() {
    return createdBy;
  }

  public void setCreatedBy(Long createdBy) {
    this.createdBy = createdBy;
  }

  public String getChangeReason() {
    return changeReason;
  }

  public void setChangeReason(String changeReason) {
    this.changeReason = changeReason;
  }

  /**
   * Create a new version of this mapping with updated values.
   * The current mapping will be marked as not current.
   *
   * @return a new ReportMapping instance as the next version
   */
  public ReportMapping createNewVersion() {
    ReportMapping newVersion = new ReportMapping();
    newVersion.setCompanyId(this.companyId);
    newVersion.setReportType(this.reportType);
    newVersion.setLineCode(this.lineCode);
    newVersion.setLineName(this.lineName);
    newVersion.setLineNameEnglish(this.lineNameEnglish);
    newVersion.setAccountPattern(this.accountPattern);
    newVersion.setOperator(this.operator);
    newVersion.setSignModifier(this.signModifier);
    newVersion.setDisplayOrder(this.displayOrder);
    newVersion.setParentLineCode(this.parentLineCode);
    newVersion.setLevel(this.level);
    newVersion.setIsCalculated(this.isCalculated);
    newVersion.setFormula(this.formula);
    newVersion.setVersion(this.version + 1);
    newVersion.setIsCurrent(true);
    newVersion.setCreatedBy(this.createdBy);
    return newVersion;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ReportMapping that = (ReportMapping) o;
    return Objects.equals(id, that.id);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }

  @Override
  public String toString() {
    return "ReportMapping{" +
        "id=" + id +
        ", companyId=" + companyId +
        ", reportType='" + reportType + '\'' +
        ", lineCode='" + lineCode + '\'' +
        ", lineName='" + lineName + '\'' +
        ", version=" + version +
        ", isCurrent=" + isCurrent +
        '}';
  }
}
