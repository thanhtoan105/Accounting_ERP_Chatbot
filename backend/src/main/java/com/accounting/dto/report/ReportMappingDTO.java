package com.accounting.dto.report;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO for TT200 report mapping configuration.
 * Used for viewing and editing account-to-line mappings.
 */
public class ReportMappingDTO {

  private UUID id;
  private String reportType;          // 'B01', 'B02', 'B03', 'F01'
  private String lineCode;            // TT200 line code
  private String lineName;            // Vietnamese line name
  private String lineNameEnglish;     // English translation
  private String accountPattern;      // Account codes/ranges
  private String operator;            // 'SUM', 'DIFF', 'ABS', 'CALC'
  private Integer signModifier;       // 1 or -1
  private Integer displayOrder;
  private String parentLineCode;
  private Integer level;
  private Boolean isCalculated;
  private String formula;
  private Integer version;
  private Boolean isCurrent;
  private Instant createdAt;
  private Instant updatedAt;
  private String createdByName;
  private String changeReason;

  public ReportMappingDTO() {
  }

  // Getters and Setters

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
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

  public String getCreatedByName() {
    return createdByName;
  }

  public void setCreatedByName(String createdByName) {
    this.createdByName = createdByName;
  }

  public String getChangeReason() {
    return changeReason;
  }

  public void setChangeReason(String changeReason) {
    this.changeReason = changeReason;
  }
}
