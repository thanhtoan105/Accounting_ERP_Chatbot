package com.accounting.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.accounting.entity.PeriodStatus;

/**
 * DTO for AccountingPeriod details.
 */
public class AccountingPeriodDTO {

  private UUID id;
  private Long companyId;
  private Integer fiscalYear;
  private Integer periodNumber;
  private String periodName;
  private LocalDate startDate;
  private LocalDate endDate;
  private PeriodStatus status;
  private String statusDisplay;
  private Long closedBy;
  private String closedByName;
  private Instant closedAt;
  private String closeReason;
  private Instant createdAt;
  private Instant updatedAt;
  private Long version;

  public AccountingPeriodDTO() {}

  public AccountingPeriodDTO(UUID id, Long companyId, Integer fiscalYear, Integer periodNumber,
                            String periodName, LocalDate startDate, LocalDate endDate,
                            PeriodStatus status, String statusDisplay, Long closedBy,
                            String closedByName, Instant closedAt, String closeReason,
                            Instant createdAt, Instant updatedAt, Long version) {
    this.id = id;
    this.companyId = companyId;
    this.fiscalYear = fiscalYear;
    this.periodNumber = periodNumber;
    this.periodName = periodName;
    this.startDate = startDate;
    this.endDate = endDate;
    this.status = status;
    this.statusDisplay = statusDisplay;
    this.closedBy = closedBy;
    this.closedByName = closedByName;
    this.closedAt = closedAt;
    this.closeReason = closeReason;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
    this.version = version;
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

  public Integer getFiscalYear() {
    return fiscalYear;
  }

  public void setFiscalYear(Integer fiscalYear) {
    this.fiscalYear = fiscalYear;
  }

  public Integer getPeriodNumber() {
    return periodNumber;
  }

  public void setPeriodNumber(Integer periodNumber) {
    this.periodNumber = periodNumber;
  }

  public String getPeriodName() {
    return periodName;
  }

  public void setPeriodName(String periodName) {
    this.periodName = periodName;
  }

  public LocalDate getStartDate() {
    return startDate;
  }

  public void setStartDate(LocalDate startDate) {
    this.startDate = startDate;
  }

  public LocalDate getEndDate() {
    return endDate;
  }

  public void setEndDate(LocalDate endDate) {
    this.endDate = endDate;
  }

  public PeriodStatus getStatus() {
    return status;
  }

  public void setStatus(PeriodStatus status) {
    this.status = status;
    this.statusDisplay = status != null ? status.getDisplayName() : null;
  }

  public String getStatusDisplay() {
    return statusDisplay;
  }

  public void setStatusDisplay(String statusDisplay) {
    this.statusDisplay = statusDisplay;
  }

  public Long getClosedBy() {
    return closedBy;
  }

  public void setClosedBy(Long closedBy) {
    this.closedBy = closedBy;
  }

  public String getClosedByName() {
    return closedByName;
  }

  public void setClosedByName(String closedByName) {
    this.closedByName = closedByName;
  }

  public Instant getClosedAt() {
    return closedAt;
  }

  public void setClosedAt(Instant closedAt) {
    this.closedAt = closedAt;
  }

  public String getCloseReason() {
    return closeReason;
  }

  public void setCloseReason(String closeReason) {
    this.closeReason = closeReason;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }

  public Long getVersion() {
    return version;
  }

  public void setVersion(Long version) {
    this.version = version;
  }

  @Override
  public String toString() {
    return "AccountingPeriodDTO{" +
        "id=" + id +
        ", companyId=" + companyId +
        ", fiscalYear=" + fiscalYear +
        ", periodNumber=" + periodNumber +
        ", periodName='" + periodName + '\'' +
        ", startDate=" + startDate +
        ", endDate=" + endDate +
        ", status=" + status +
        '}';
  }
}