package com.accounting.dto.report;

import java.time.Instant;
import java.util.Map;

/**
 * DTO representing a mapping version history entry.
 * Used for viewing mapping change history and rollback options.
 */
public class MappingVersionDTO {

  private Integer version;
  private String lineCode;
  private String reportType;
  private String accountPattern;
  private String operator;
  private String formula;
  private String changeReason;
  private Instant createdAt;
  private String createdByName;
  private Boolean isCurrent;

  // Diff from previous version (if available)
  private Map<String, Object> diff;

  public MappingVersionDTO() {
  }

  // Getters and Setters

  public Integer getVersion() {
    return version;
  }

  public void setVersion(Integer version) {
    this.version = version;
  }

  public String getLineCode() {
    return lineCode;
  }

  public void setLineCode(String lineCode) {
    this.lineCode = lineCode;
  }

  public String getReportType() {
    return reportType;
  }

  public void setReportType(String reportType) {
    this.reportType = reportType;
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

  public String getFormula() {
    return formula;
  }

  public void setFormula(String formula) {
    this.formula = formula;
  }

  public String getChangeReason() {
    return changeReason;
  }

  public void setChangeReason(String changeReason) {
    this.changeReason = changeReason;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public String getCreatedByName() {
    return createdByName;
  }

  public void setCreatedByName(String createdByName) {
    this.createdByName = createdByName;
  }

  public Boolean getIsCurrent() {
    return isCurrent;
  }

  public void setIsCurrent(Boolean isCurrent) {
    this.isCurrent = isCurrent;
  }

  public Map<String, Object> getDiff() {
    return diff;
  }

  public void setDiff(Map<String, Object> diff) {
    this.diff = diff;
  }
}
