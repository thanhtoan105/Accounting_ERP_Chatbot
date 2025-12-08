package com.accounting.entity.audit;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * Entity representing an integrity check execution result.
 * Stores the outcome of daily and period-close integrity checks.
 */
@Entity
@Table(name = "integrity_check_results")
public class IntegrityCheckResult {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "company_id", nullable = false)
  private Long companyId;

  @Enumerated(EnumType.STRING)
  @Column(name = "check_type", nullable = false, length = 20)
  private CheckType checkType;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private CheckStatus status;

  @Column(name = "period_id")
  private UUID periodId;

  @Column(name = "executed_at", nullable = false)
  private Instant executedAt;

  @Column(name = "duration_ms")
  private Long durationMs;

  @Column(name = "records_checked")
  private Integer recordsChecked;

  @Column(name = "issue_count")
  private Integer issueCount;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "issues", columnDefinition = "jsonb")
  private String issuesJson;

  @Column(name = "alerts_sent")
  private Boolean alertsSent;

  public enum CheckType {
    DAILY,
    PERIOD_CLOSE,
    MANUAL,
    HOURLY_ANOMALY
  }

  public enum CheckStatus {
    PASSED,
    FAILED,
    ERROR
  }

  @PrePersist
  public void prePersist() {
    if (executedAt == null) {
      executedAt = Instant.now();
    }
    if (alertsSent == null) {
      alertsSent = false;
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

  public CheckType getCheckType() {
    return checkType;
  }

  public void setCheckType(CheckType checkType) {
    this.checkType = checkType;
  }

  public CheckStatus getStatus() {
    return status;
  }

  public void setStatus(CheckStatus status) {
    this.status = status;
  }

  public UUID getPeriodId() {
    return periodId;
  }

  public void setPeriodId(UUID periodId) {
    this.periodId = periodId;
  }

  public Instant getExecutedAt() {
    return executedAt;
  }

  public void setExecutedAt(Instant executedAt) {
    this.executedAt = executedAt;
  }

  public Long getDurationMs() {
    return durationMs;
  }

  public void setDurationMs(Long durationMs) {
    this.durationMs = durationMs;
  }

  public Integer getRecordsChecked() {
    return recordsChecked;
  }

  public void setRecordsChecked(Integer recordsChecked) {
    this.recordsChecked = recordsChecked;
  }

  public Integer getIssueCount() {
    return issueCount;
  }

  public void setIssueCount(Integer issueCount) {
    this.issueCount = issueCount;
  }

  public String getIssuesJson() {
    return issuesJson;
  }

  public void setIssuesJson(String issuesJson) {
    this.issuesJson = issuesJson;
  }

  public Boolean getAlertsSent() {
    return alertsSent;
  }

  public void setAlertsSent(Boolean alertsSent) {
    this.alertsSent = alertsSent;
  }

  /**
   * Mark this check as passed.
   */
  public void markPassed(long durationMs, int recordsChecked) {
    this.status = CheckStatus.PASSED;
    this.durationMs = durationMs;
    this.recordsChecked = recordsChecked;
    this.issueCount = 0;
  }

  /**
   * Mark this check as failed with issues.
   */
  public void markFailed(long durationMs, int recordsChecked, int issueCount, String issuesJson) {
    this.status = CheckStatus.FAILED;
    this.durationMs = durationMs;
    this.recordsChecked = recordsChecked;
    this.issueCount = issueCount;
    this.issuesJson = issuesJson;
  }
}
