package com.accounting.entity.audit;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

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
 * Entity representing an audit log purge request.
 * Requires dual approval: Chief Accountant requests, Admin approves.
 */
@Entity
@Table(name = "audit_purge_requests")
public class AuditPurgeRequest {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "company_id", nullable = false)
  private Long companyId;

  @Column(name = "requester_id", nullable = false)
  private Long requesterId;

  @Column(name = "approver_id")
  private Long approverId;

  @Column(name = "date_from", nullable = false)
  private LocalDate dateFrom;

  @Column(name = "date_to", nullable = false)
  private LocalDate dateTo;

  @Column(name = "reason", nullable = false, length = 1000)
  private String reason;

  @Column(name = "rejection_reason", length = 500)
  private String rejectionReason;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private PurgeStatus status = PurgeStatus.PENDING;

  @Column(name = "estimated_records")
  private Integer estimatedRecords;

  @Column(name = "records_purged")
  private Integer recordsPurged;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "processed_at")
  private Instant processedAt;

  public enum PurgeStatus {
    PENDING,
    APPROVED,
    REJECTED
  }

  @PrePersist
  public void prePersist() {
    if (createdAt == null) {
      createdAt = Instant.now();
    }
    if (status == null) {
      status = PurgeStatus.PENDING;
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

  public Long getRequesterId() {
    return requesterId;
  }

  public void setRequesterId(Long requesterId) {
    this.requesterId = requesterId;
  }

  public Long getApproverId() {
    return approverId;
  }

  public void setApproverId(Long approverId) {
    this.approverId = approverId;
  }

  public LocalDate getDateFrom() {
    return dateFrom;
  }

  public void setDateFrom(LocalDate dateFrom) {
    this.dateFrom = dateFrom;
  }

  public LocalDate getDateTo() {
    return dateTo;
  }

  public void setDateTo(LocalDate dateTo) {
    this.dateTo = dateTo;
  }

  public String getReason() {
    return reason;
  }

  public void setReason(String reason) {
    this.reason = reason;
  }

  public String getRejectionReason() {
    return rejectionReason;
  }

  public void setRejectionReason(String rejectionReason) {
    this.rejectionReason = rejectionReason;
  }

  public PurgeStatus getStatus() {
    return status;
  }

  public void setStatus(PurgeStatus status) {
    this.status = status;
  }

  public Integer getEstimatedRecords() {
    return estimatedRecords;
  }

  public void setEstimatedRecords(Integer estimatedRecords) {
    this.estimatedRecords = estimatedRecords;
  }

  public Integer getRecordsPurged() {
    return recordsPurged;
  }

  public void setRecordsPurged(Integer recordsPurged) {
    this.recordsPurged = recordsPurged;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public Instant getProcessedAt() {
    return processedAt;
  }

  public void setProcessedAt(Instant processedAt) {
    this.processedAt = processedAt;
  }

  /**
   * Mark this request as approved.
   */
  public void approve(Long approverId, int recordsPurged) {
    this.approverId = approverId;
    this.status = PurgeStatus.APPROVED;
    this.recordsPurged = recordsPurged;
    this.processedAt = Instant.now();
  }

  /**
   * Mark this request as rejected.
   */
  public void reject(Long rejectedById, String reason) {
    this.approverId = rejectedById;
    this.status = PurgeStatus.REJECTED;
    this.rejectionReason = reason;
    this.processedAt = Instant.now();
  }
}
