package com.accounting.dto.audit;

import java.time.Instant;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * DTO representing a single audit log entry in the Cash & Bank audit explorer.
 */
public class CashAuditLogDTO {

  private Long id;
  private String action;
  private String entityType;
  private String entityId;
  private String entityDisplay;
  private Long userId;
  private String userEmail;
  private String actorRole;
  private Instant timestamp;
  private JsonNode details;
  private JsonNode metadata;
  private Boolean success;
  private String failureReason;
  private String ipAddress;
  private String traceId;

  public CashAuditLogDTO() {
  }

  // Getters and Setters

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getAction() {
    return action;
  }

  public void setAction(String action) {
    this.action = action;
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

  public Long getUserId() {
    return userId;
  }

  public void setUserId(Long userId) {
    this.userId = userId;
  }

  public String getUserEmail() {
    return userEmail;
  }

  public void setUserEmail(String userEmail) {
    this.userEmail = userEmail;
  }

  public String getActorRole() {
    return actorRole;
  }

  public void setActorRole(String actorRole) {
    this.actorRole = actorRole;
  }

  public Instant getTimestamp() {
    return timestamp;
  }

  public void setTimestamp(Instant timestamp) {
    this.timestamp = timestamp;
  }

  public JsonNode getDetails() {
    return details;
  }

  public void setDetails(JsonNode details) {
    this.details = details;
  }

  public JsonNode getMetadata() {
    return metadata;
  }

  public void setMetadata(JsonNode metadata) {
    this.metadata = metadata;
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

  public String getIpAddress() {
    return ipAddress;
  }

  public void setIpAddress(String ipAddress) {
    this.ipAddress = ipAddress;
  }

  public String getTraceId() {
    return traceId;
  }

  public void setTraceId(String traceId) {
    this.traceId = traceId;
  }

  /**
   * Builder for creating CashAuditLogDTO instances.
   */
  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private final CashAuditLogDTO dto = new CashAuditLogDTO();

    public Builder id(Long id) {
      dto.id = id;
      return this;
    }

    public Builder action(String action) {
      dto.action = action;
      return this;
    }

    public Builder entityType(String entityType) {
      dto.entityType = entityType;
      return this;
    }

    public Builder entityId(String entityId) {
      dto.entityId = entityId;
      return this;
    }

    public Builder entityDisplay(String entityDisplay) {
      dto.entityDisplay = entityDisplay;
      return this;
    }

    public Builder userId(Long userId) {
      dto.userId = userId;
      return this;
    }

    public Builder userEmail(String userEmail) {
      dto.userEmail = userEmail;
      return this;
    }

    public Builder actorRole(String actorRole) {
      dto.actorRole = actorRole;
      return this;
    }

    public Builder timestamp(Instant timestamp) {
      dto.timestamp = timestamp;
      return this;
    }

    public Builder details(JsonNode details) {
      dto.details = details;
      return this;
    }

    public Builder metadata(JsonNode metadata) {
      dto.metadata = metadata;
      return this;
    }

    public Builder success(Boolean success) {
      dto.success = success;
      return this;
    }

    public Builder failureReason(String failureReason) {
      dto.failureReason = failureReason;
      return this;
    }

    public Builder ipAddress(String ipAddress) {
      dto.ipAddress = ipAddress;
      return this;
    }

    public Builder traceId(String traceId) {
      dto.traceId = traceId;
      return this;
    }

    public CashAuditLogDTO build() {
      return dto;
    }
  }
}
