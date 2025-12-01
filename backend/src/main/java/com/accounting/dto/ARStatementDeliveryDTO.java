package com.accounting.dto;

import com.accounting.entity.ARStatementDelivery;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO for AR statement delivery record.
 */
public class ARStatementDeliveryDTO {

  private UUID id;
  private UUID statementId;
  private Long customerId;
  private String recipientEmail;
  private Instant sentAt;
  private ARStatementDelivery.DeliveryStatus status;
  private String deliveryTrackingId;
  private String failureReason;
  private Instant createdAt;

  // Getters and setters
  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public UUID getStatementId() {
    return statementId;
  }

  public void setStatementId(UUID statementId) {
    this.statementId = statementId;
  }

  public Long getCustomerId() {
    return customerId;
  }

  public void setCustomerId(Long customerId) {
    this.customerId = customerId;
  }

  public String getRecipientEmail() {
    return recipientEmail;
  }

  public void setRecipientEmail(String recipientEmail) {
    this.recipientEmail = recipientEmail;
  }

  public Instant getSentAt() {
    return sentAt;
  }

  public void setSentAt(Instant sentAt) {
    this.sentAt = sentAt;
  }

  public ARStatementDelivery.DeliveryStatus getStatus() {
    return status;
  }

  public void setStatus(ARStatementDelivery.DeliveryStatus status) {
    this.status = status;
  }

  public String getDeliveryTrackingId() {
    return deliveryTrackingId;
  }

  public void setDeliveryTrackingId(String deliveryTrackingId) {
    this.deliveryTrackingId = deliveryTrackingId;
  }

  public String getFailureReason() {
    return failureReason;
  }

  public void setFailureReason(String failureReason) {
    this.failureReason = failureReason;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }
}

