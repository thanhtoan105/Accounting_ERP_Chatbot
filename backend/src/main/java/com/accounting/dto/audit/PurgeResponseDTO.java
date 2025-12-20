package com.accounting.dto.audit;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * DTO for purge request response.
 */
public class PurgeResponseDTO {

  private UUID requestId;
  private String status;
  private Long requestedBy;
  private Long approvedBy;
  private LocalDate dateFrom;
  private LocalDate dateTo;
  private String reason;
  private Integer recordsPurged;
  private Integer estimatedRecords;
  private Instant createdAt;
  private Instant completedAt;

  public PurgeResponseDTO() {
  }

  /**
   * Create a pending purge request response.
   */
  public static PurgeResponseDTO pending(
      UUID requestId, Long requestedBy, LocalDate dateFrom, LocalDate dateTo,
      String reason, int estimatedRecords) {
    PurgeResponseDTO dto = new PurgeResponseDTO();
    dto.requestId = requestId;
    dto.status = "PENDING_APPROVAL";
    dto.requestedBy = requestedBy;
    dto.dateFrom = dateFrom;
    dto.dateTo = dateTo;
    dto.reason = reason;
    dto.estimatedRecords = estimatedRecords;
    dto.createdAt = Instant.now();
    return dto;
  }

  /**
   * Create an approved/completed purge response.
   */
  public static PurgeResponseDTO approved(
      UUID requestId, Long requestedBy, Long approvedBy, int recordsPurged) {
    PurgeResponseDTO dto = new PurgeResponseDTO();
    dto.requestId = requestId;
    dto.status = "APPROVED";
    dto.requestedBy = requestedBy;
    dto.approvedBy = approvedBy;
    dto.recordsPurged = recordsPurged;
    dto.completedAt = Instant.now();
    return dto;
  }

  /**
   * Create a rejected purge response.
   */
  public static PurgeResponseDTO rejected(UUID requestId, Long requestedBy, Long rejectedBy) {
    PurgeResponseDTO dto = new PurgeResponseDTO();
    dto.requestId = requestId;
    dto.status = "REJECTED";
    dto.requestedBy = requestedBy;
    dto.approvedBy = rejectedBy;
    dto.completedAt = Instant.now();
    return dto;
  }

  // Getters and Setters

  public UUID getRequestId() {
    return requestId;
  }

  public void setRequestId(UUID requestId) {
    this.requestId = requestId;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public Long getRequestedBy() {
    return requestedBy;
  }

  public void setRequestedBy(Long requestedBy) {
    this.requestedBy = requestedBy;
  }

  public Long getApprovedBy() {
    return approvedBy;
  }

  public void setApprovedBy(Long approvedBy) {
    this.approvedBy = approvedBy;
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

  public Integer getRecordsPurged() {
    return recordsPurged;
  }

  public void setRecordsPurged(Integer recordsPurged) {
    this.recordsPurged = recordsPurged;
  }

  public Integer getEstimatedRecords() {
    return estimatedRecords;
  }

  public void setEstimatedRecords(Integer estimatedRecords) {
    this.estimatedRecords = estimatedRecords;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public Instant getCompletedAt() {
    return completedAt;
  }

  public void setCompletedAt(Instant completedAt) {
    this.completedAt = completedAt;
  }
}
