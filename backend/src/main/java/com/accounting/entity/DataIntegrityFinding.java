package com.accounting.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "data_integrity_findings")
public class DataIntegrityFinding {

  @Id
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "job_id", nullable = false)
  private DataIntegrityJob job;

  @Column(name = "entity_type", nullable = false, length = 100)
  private String entityType;

  @Column(name = "entity_id", length = 64)
  private String entityId;

  @Column(name = "issue_type", nullable = false, length = 100)
  private String issueType;

  @Column(name = "description", nullable = false)
  private String description;

  @Enumerated(EnumType.STRING)
  @Column(name = "severity", length = 20)
  private DataIntegritySeverity severity = DataIntegritySeverity.MEDIUM;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "metadata", columnDefinition = "jsonb")
  private JsonNode metadata;

  public DataIntegrityFinding() {
    this.id = UUID.randomUUID();
  }

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public DataIntegrityJob getJob() {
    return job;
  }

  public void setJob(DataIntegrityJob job) {
    this.job = job;
  }

  public String getEntityType() {
    return entityType;
  }

  public void setEntityType(String entityType) {
    this.entityType = entityType;
  }

  public String getEntityId() {
    return entityId;
  }

  public void setEntityId(String entityId) {
    this.entityId = entityId;
  }

  public String getIssueType() {
    return issueType;
  }

  public void setIssueType(String issueType) {
    this.issueType = issueType;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public DataIntegritySeverity getSeverity() {
    return severity;
  }

  public void setSeverity(DataIntegritySeverity severity) {
    this.severity = severity;
  }

  public JsonNode getMetadata() {
    return metadata;
  }

  public void setMetadata(JsonNode metadata) {
    this.metadata = metadata;
  }
}
