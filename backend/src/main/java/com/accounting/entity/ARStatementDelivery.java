package com.accounting.entity;

import com.accounting.repository.CompanyScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

/**
 * ARStatementDelivery entity for tracking email delivery of customer statements.
 * Maintains delivery status, tracking IDs, and failure reasons for audit compliance.
 */
@Entity
@Table(name = "ar_statement_delivery")
public class ARStatementDelivery implements CompanyScopedEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @NotNull
  @Column(name = "company_id", nullable = false)
  private Long companyId;

  @NotNull
  @Column(name = "statement_id", nullable = false)
  private UUID statementId;

  @NotNull
  @Column(name = "customer_id", nullable = false)
  private Long customerId;

  @NotNull
  @Email
  @Size(max = 255)
  @Column(name = "recipient_email", nullable = false, length = 255)
  private String recipientEmail;

  @NotNull
  @Column(name = "sent_at", nullable = false)
  private Instant sentAt;

  @NotNull
  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private DeliveryStatus status = DeliveryStatus.SENT;

  @Size(max = 255)
  @Column(name = "delivery_tracking_id", length = 255)
  private String deliveryTrackingId;

  @Size(max = 1000)
  @Column(name = "failure_reason", length = 1000)
  private String failureReason;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  // Relationships
  @ManyToOne
  @JoinColumn(name = "company_id", insertable = false, updatable = false)
  private Company company;

  @ManyToOne
  @JoinColumn(name = "statement_id", insertable = false, updatable = false)
  private ARStatementHistory statement;

  @ManyToOne
  @JoinColumn(name = "customer_id", insertable = false, updatable = false)
  private Customer customer;

  @PrePersist
  protected void onCreate() {
    if (sentAt == null) {
      sentAt = Instant.now();
    }
    if (createdAt == null) {
      createdAt = Instant.now();
    }
  }

  // Enum for delivery status
  public enum DeliveryStatus {
    SENT,
    DELIVERED,
    FAILED
  }

  // Getters and setters
  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  @Override
  public Long getCompanyId() {
    return companyId;
  }

  public void setCompanyId(Long companyId) {
    this.companyId = companyId;
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

  public DeliveryStatus getStatus() {
    return status;
  }

  public void setStatus(DeliveryStatus status) {
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

  public Company getCompany() {
    return company;
  }

  public void setCompany(Company company) {
    this.company = company;
  }

  public ARStatementHistory getStatement() {
    return statement;
  }

  public void setStatement(ARStatementHistory statement) {
    this.statement = statement;
  }

  public Customer getCustomer() {
    return customer;
  }

  public void setCustomer(Customer customer) {
    this.customer = customer;
  }
}

