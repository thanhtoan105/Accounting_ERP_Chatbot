package com.accounting.entity;

import com.accounting.repository.CompanyScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.Instant;
import java.util.UUID;

/**
 * AccountingPeriod entity representing fiscal periods in the accounting system.
 * Periods control voucher posting workflows and financial reporting boundaries.
 */
@Entity
@Table(name = "accounting_periods")
public class AccountingPeriod implements CompanyScopedEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @NotNull
  @Column(name = "company_id", nullable = false)
  private Long companyId;

  @NotNull
  @Column(name = "fiscal_year", nullable = false)
  private Integer fiscalYear;

  @NotNull
  @Column(name = "period_number", nullable = false)
  private Integer periodNumber; // 1-12 for monthly periods

  @NotBlank
  @Column(name = "period_name", nullable = false, length = 100)
  private String periodName; // e.g., "January 2025", "Q1 2025"

  @NotNull
  @Column(name = "start_date", nullable = false)
  private LocalDate startDate;

  @NotNull
  @Column(name = "end_date", nullable = false)
  private LocalDate endDate;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private PeriodStatus status = PeriodStatus.OPEN;

  @Column(name = "closed_by")
  private Long closedBy; // User ID who closed the period

  @Column(name = "closed_at")
  private Instant closedAt; // Timestamp when period was closed

  @Column(name = "close_reason", length = 500)
  private String closeReason; // Reason for closing the period

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(name = "version", nullable = false)
  private Long version = 0L;

  // Default constructor
  public AccountingPeriod() {
    this.createdAt = Instant.now();
    this.updatedAt = Instant.now();
  }

  // Constructors
  public AccountingPeriod(Long companyId, Integer fiscalYear, Integer periodNumber,
                         String periodName, LocalDate startDate, LocalDate endDate) {
    this();
    this.companyId = companyId;
    this.fiscalYear = fiscalYear;
    this.periodNumber = periodNumber;
    this.periodName = periodName;
    this.startDate = startDate;
    this.endDate = endDate;
  }

  // Getters and Setters
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
  }

  public Long getClosedBy() {
    return closedBy;
  }

  public void setClosedBy(Long closedBy) {
    this.closedBy = closedBy;
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

  // Business logic methods
  public boolean isOpen() {
    return status == PeriodStatus.OPEN;
  }

  public boolean isClosed() {
    return status == PeriodStatus.CLOSED;
  }

  public boolean isFuture() {
    return startDate.isAfter(LocalDate.now());
  }

  public void closePeriod(Long closedBy, String closeReason) {
    this.status = PeriodStatus.CLOSED;
    this.closedBy = closedBy;
    this.closedAt = Instant.now();
    this.closeReason = closeReason;
    this.updatedAt = Instant.now();
  }

  public void reopenPeriod() {
    this.status = PeriodStatus.OPEN;
    this.closedBy = null;
    this.closedAt = null;
    this.closeReason = null;
    this.updatedAt = Instant.now();
  }

  @jakarta.persistence.PreUpdate
  protected void onUpdate() {
    this.updatedAt = Instant.now();
  }

  @Override
  public String toString() {
    return "AccountingPeriod{" +
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