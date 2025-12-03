package com.accounting.dto.audit;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * DTO representing the result of an integrity check operation.
 */
public class IntegrityCheckResultDTO {

  private UUID checkId;
  private String checkType; // DAILY, PERIOD_CLOSE
  private boolean passed;
  private Instant executedAt;
  private long duration; // milliseconds
  private int recordsChecked;
  private int issueCount;
  private List<IntegrityIssueDTO> issues;
  private boolean alertsSent;

  public IntegrityCheckResultDTO() {
  }

  /**
   * Create a passed check result.
   */
  public static IntegrityCheckResultDTO passed(UUID checkId, String checkType,
      long duration, int recordsChecked) {
    IntegrityCheckResultDTO result = new IntegrityCheckResultDTO();
    result.checkId = checkId;
    result.checkType = checkType;
    result.passed = true;
    result.executedAt = Instant.now();
    result.duration = duration;
    result.recordsChecked = recordsChecked;
    result.issueCount = 0;
    result.issues = List.of();
    result.alertsSent = false;
    return result;
  }

  /**
   * Create a failed check result with issues.
   */
  public static IntegrityCheckResultDTO failed(UUID checkId, String checkType,
      long duration, int recordsChecked, List<IntegrityIssueDTO> issues) {
    IntegrityCheckResultDTO result = new IntegrityCheckResultDTO();
    result.checkId = checkId;
    result.checkType = checkType;
    result.passed = false;
    result.executedAt = Instant.now();
    result.duration = duration;
    result.recordsChecked = recordsChecked;
    result.issueCount = issues.size();
    result.issues = issues;
    result.alertsSent = false;
    return result;
  }

  // Getters and Setters

  public UUID getCheckId() {
    return checkId;
  }

  public void setCheckId(UUID checkId) {
    this.checkId = checkId;
  }

  public String getCheckType() {
    return checkType;
  }

  public void setCheckType(String checkType) {
    this.checkType = checkType;
  }

  public boolean isPassed() {
    return passed;
  }

  public void setPassed(boolean passed) {
    this.passed = passed;
  }

  public Instant getExecutedAt() {
    return executedAt;
  }

  public void setExecutedAt(Instant executedAt) {
    this.executedAt = executedAt;
  }

  public long getDuration() {
    return duration;
  }

  public void setDuration(long duration) {
    this.duration = duration;
  }

  public int getRecordsChecked() {
    return recordsChecked;
  }

  public void setRecordsChecked(int recordsChecked) {
    this.recordsChecked = recordsChecked;
  }

  public int getIssueCount() {
    return issueCount;
  }

  public void setIssueCount(int issueCount) {
    this.issueCount = issueCount;
  }

  public List<IntegrityIssueDTO> getIssues() {
    return issues;
  }

  public void setIssues(List<IntegrityIssueDTO> issues) {
    this.issues = issues;
  }

  public boolean isAlertsSent() {
    return alertsSent;
  }

  public void setAlertsSent(boolean alertsSent) {
    this.alertsSent = alertsSent;
  }
}
