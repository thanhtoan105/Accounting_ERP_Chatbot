package com.accounting.entity;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "audit_logs")
public class AuditLog {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id")
  private Long userId;

  @Column(name = "email", length = 255)
  private String email;

  @Column(name = "actor_role", length = 50)
  private String actorRole;

  @Column(name = "company_id")
  private Long companyId;

  @Column(name = "entity_type", length = 100)
  private String entityType;

  @Column(name = "entity_id", length = 64)
  private String entityId;

  @Column(name = "entity_display", length = 255)
  private String entityDisplay;

  @Column(name = "event_type", length = 50)
  private String eventType;

  @Column(name = "action", nullable = false, length = 50)
  private String action;

  @Column(name = "reason", length = 50)
  private String reason;

  @Column(name = "ip_address", length = 45)
  private String ipAddress;

  @Column(name = "user_agent", length = 512)
  private String userAgent;

  @Column(name = "success")
  private Boolean success;

  @Column(name = "failure_reason", length = 255)
  private String failureReason;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "changes", columnDefinition = "jsonb")
  private JsonNode changes;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "metadata", columnDefinition = "jsonb")
  private JsonNode metadata;

  @Column(name = "trace_id", length = 64)
  private String traceId;

  @Column(name = "chain_hash", length = 64)
  private String chainHash;

  @Column(name = "retention_until")
  private Instant retentionUntil;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @PrePersist
  public void prePersist() {
    if (createdAt == null) {
      createdAt = Instant.now();
    }
    // Default retention: 10 years
    if (retentionUntil == null) {
      retentionUntil = createdAt.atZone(java.time.ZoneId.systemDefault())
          .plusYears(10).toInstant();
    }
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public Long getUserId() {
    return userId;
  }

  public void setUserId(Long userId) {
    this.userId = userId;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public String getActorRole() {
    return actorRole;
  }

  public void setActorRole(String actorRole) {
    this.actorRole = actorRole;
  }

  public Long getCompanyId() {
    return companyId;
  }

  public void setCompanyId(Long companyId) {
    this.companyId = companyId;
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

  public String getEntityDisplay() {
    return entityDisplay;
  }

  public void setEntityDisplay(String entityDisplay) {
    this.entityDisplay = entityDisplay;
  }

  public String getEventType() {
    return eventType;
  }

  public void setEventType(String eventType) {
    this.eventType = eventType;
  }

  public String getAction() {
    return action;
  }

  public void setAction(String action) {
    this.action = action;
  }

  public String getReason() {
    return reason;
  }

  public void setReason(String reason) {
    this.reason = reason;
  }

  public String getIpAddress() {
    return ipAddress;
  }

  public void setIpAddress(String ipAddress) {
    this.ipAddress = ipAddress;
  }

  public String getUserAgent() {
    return userAgent;
  }

  public void setUserAgent(String userAgent) {
    this.userAgent = userAgent;
  }

  public Boolean getSuccess() {
    return success;
  }

  public void setSuccess(Boolean success) {
    this.success = success;
  }

  public String getFailureReason() {
    return failureReason;
  }

  public void setFailureReason(String failureReason) {
    this.failureReason = failureReason;
  }

  public JsonNode getChanges() {
    return changes;
  }

  public void setChanges(JsonNode changes) {
    this.changes = changes;
  }

  public JsonNode getMetadata() {
    return metadata;
  }

  public void setMetadata(JsonNode metadata) {
    this.metadata = metadata;
  }

  public String getTraceId() {
    return traceId;
  }

  public void setTraceId(String traceId) {
    this.traceId = traceId;
  }

  public String getChainHash() {
    return chainHash;
  }

  public void setChainHash(String chainHash) {
    this.chainHash = chainHash;
  }

  public Instant getRetentionUntil() {
    return retentionUntil;
  }

  public void setRetentionUntil(Instant retentionUntil) {
    this.retentionUntil = retentionUntil;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public AuditLog markFailed(String failure, JsonNode metadataNode) {
    this.success = Boolean.FALSE;
    this.failureReason = failure;
    if (metadataNode != null) {
      this.metadata = metadataNode;
    }
    return this;
  }

  public AuditLog markSuccess(JsonNode metadataNode) {
    this.success = Boolean.TRUE;
    if (metadataNode != null) {
      this.metadata = metadataNode;
    }
    return this;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;
    AuditLog auditLog = (AuditLog) o;
    return Objects.equals(id, auditLog.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
