package com.accounting.entity;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "data_integrity_jobs")
public class DataIntegrityJob {

  @Id
  private UUID id;

  @Column(name = "company_id", nullable = false)
  private Long companyId;

  @Column(name = "triggered_by_user_id", nullable = false)
  private Long triggeredByUserId;

  @Column(name = "triggered_by_email", length = 255)
  private String triggeredByEmail;

  @Column(name = "triggered_by_role", length = 50)
  private String triggeredByRole;

  @Column(name = "started_at", nullable = false)
  private Instant startedAt;

  @Column(name = "completed_at")
  private Instant completedAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private DataIntegrityJobStatus status;

  @Column(name = "findings_count")
  private int findingsCount;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "warnings", columnDefinition = "jsonb")
  private JsonNode warnings;

  @Column(name = "summary")
  private String summary;

  @Column(name = "audit_log_id")
  private Long auditLogId;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @PrePersist
  void prePersist() {
    if (id == null) {
      id = UUID.randomUUID();
    }
    if (startedAt == null) {
      startedAt = Instant.now();
    }
    if (createdAt == null) {
      createdAt = Instant.now();
    }
  }

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

  public Long getTriggeredByUserId() {
    return triggeredByUserId;
  }

  public void setTriggeredByUserId(Long triggeredByUserId) {
    this.triggeredByUserId = triggeredByUserId;
  }

  public String getTriggeredByEmail() {
    return triggeredByEmail;
  }

  public void setTriggeredByEmail(String triggeredByEmail) {
    this.triggeredByEmail = triggeredByEmail;
  }

  public String getTriggeredByRole() {
    return triggeredByRole;
  }

  public void setTriggeredByRole(String triggeredByRole) {
    this.triggeredByRole = triggeredByRole;
  }

  public Instant getStartedAt() {
    return startedAt;
  }

  public void setStartedAt(Instant startedAt) {
    this.startedAt = startedAt;
  }

  public Instant getCompletedAt() {
    return completedAt;
  }

  public void setCompletedAt(Instant completedAt) {
    this.completedAt = completedAt;
  }

  public DataIntegrityJobStatus getStatus() {
    return status;
  }

  public void setStatus(DataIntegrityJobStatus status) {
    this.status = status;
  }

  public int getFindingsCount() {
    return findingsCount;
  }

  public void setFindingsCount(int findingsCount) {
    this.findingsCount = findingsCount;
  }

  public JsonNode getWarnings() {
    return warnings;
  }

  public void setWarnings(JsonNode warnings) {
    this.warnings = warnings;
  }

  public String getSummary() {
    return summary;
  }

  public void setSummary(String summary) {
    this.summary = summary;
  }

  public Long getAuditLogId() {
    return auditLogId;
  }

  public void setAuditLogId(Long auditLogId) {
    this.auditLogId = auditLogId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }
}
