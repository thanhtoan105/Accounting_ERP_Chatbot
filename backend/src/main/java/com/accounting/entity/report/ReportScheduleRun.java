package com.accounting.entity.report;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.accounting.entity.AccountingPeriod;
import com.accounting.entity.Company;
import com.accounting.entity.User;
import com.accounting.repository.CompanyScopedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "report_schedule_runs")
public class ReportScheduleRun implements CompanyScopedEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "company_id", nullable = false)
  private Long companyId;

  @Column(name = "schedule_id", nullable = false)
  private UUID scheduleId;

  @Column(name = "period_id")
  private UUID periodId;

  @Column(name = "period_label", length = 50)
  private String periodLabel;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 30)
  private RunStatus status = RunStatus.PENDING;

  @Enumerated(EnumType.STRING)
  @Column(name = "trigger_type", nullable = false, length = 20)
  private TriggerType triggerType;

  @Column(name = "triggered_by_id")
  private Long triggeredById;

  @Column(name = "snapshot_id")
  private UUID snapshotId;

  @Column(name = "rerun_of_run_id")
  private UUID rerunOfRunId;

  @Column(name = "attempt", nullable = false)
  private Integer attempt = 1;

  @Column(name = "max_attempts", nullable = false)
  private Integer maxAttempts = 3;

  @Column(name = "queued_at")
  private Instant queuedAt;

  @Column(name = "started_at")
  private Instant startedAt;

  @Column(name = "finished_at")
  private Instant finishedAt;

  @Column(name = "duration_ms")
  private Long durationMs;

  @Column(name = "error_code", length = 100)
  private String errorCode;

  @Column(name = "error_message", columnDefinition = "TEXT")
  private String errorMessage;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "partial_failure_details", columnDefinition = "jsonb")
  private String partialFailureDetails;

  @Column(name = "sla_deadline")
  private Instant slaDeadline;

  @Column(name = "exceeded_sla", nullable = false)
  private Boolean exceededSla = false;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "company_id", insertable = false, updatable = false)
  private Company company;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "schedule_id", insertable = false, updatable = false)
  private ReportSchedule schedule;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "period_id", insertable = false, updatable = false)
  private AccountingPeriod period;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "triggered_by_id", insertable = false, updatable = false)
  private User triggeredBy;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "rerun_of_run_id", insertable = false, updatable = false)
  private ReportScheduleRun rerunOfRun;

  @PrePersist
  protected void onCreate() {
    createdAt = Instant.now();
    updatedAt = Instant.now();
    if (queuedAt == null) {
      queuedAt = Instant.now();
    }
  }

  @PreUpdate
  protected void onUpdate() {
    updatedAt = Instant.now();
  }

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

  public UUID getScheduleId() {
    return scheduleId;
  }

  public void setScheduleId(UUID scheduleId) {
    this.scheduleId = scheduleId;
  }

  public UUID getPeriodId() {
    return periodId;
  }

  public void setPeriodId(UUID periodId) {
    this.periodId = periodId;
  }

  public String getPeriodLabel() {
    return periodLabel;
  }

  public void setPeriodLabel(String periodLabel) {
    this.periodLabel = periodLabel;
  }

  public RunStatus getStatus() {
    return status;
  }

  public void setStatus(RunStatus status) {
    this.status = status;
  }

  public TriggerType getTriggerType() {
    return triggerType;
  }

  public void setTriggerType(TriggerType triggerType) {
    this.triggerType = triggerType;
  }

  public Long getTriggeredById() {
    return triggeredById;
  }

  public void setTriggeredById(Long triggeredById) {
    this.triggeredById = triggeredById;
  }

  public UUID getSnapshotId() {
    return snapshotId;
  }

  public void setSnapshotId(UUID snapshotId) {
    this.snapshotId = snapshotId;
  }

  public UUID getRerunOfRunId() {
    return rerunOfRunId;
  }

  public void setRerunOfRunId(UUID rerunOfRunId) {
    this.rerunOfRunId = rerunOfRunId;
  }

  public Integer getAttempt() {
    return attempt;
  }

  public void setAttempt(Integer attempt) {
    this.attempt = attempt;
  }

  public Integer getMaxAttempts() {
    return maxAttempts;
  }

  public void setMaxAttempts(Integer maxAttempts) {
    this.maxAttempts = maxAttempts;
  }

  public Instant getQueuedAt() {
    return queuedAt;
  }

  public void setQueuedAt(Instant queuedAt) {
    this.queuedAt = queuedAt;
  }

  public Instant getStartedAt() {
    return startedAt;
  }

  public void setStartedAt(Instant startedAt) {
    this.startedAt = startedAt;
  }

  public Instant getFinishedAt() {
    return finishedAt;
  }

  public void setFinishedAt(Instant finishedAt) {
    this.finishedAt = finishedAt;
  }

  public Long getDurationMs() {
    return durationMs;
  }

  public void setDurationMs(Long durationMs) {
    this.durationMs = durationMs;
  }

  public String getErrorCode() {
    return errorCode;
  }

  public void setErrorCode(String errorCode) {
    this.errorCode = errorCode;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public void setErrorMessage(String errorMessage) {
    this.errorMessage = errorMessage;
  }

  public String getPartialFailureDetails() {
    return partialFailureDetails;
  }

  public void setPartialFailureDetails(String partialFailureDetails) {
    this.partialFailureDetails = partialFailureDetails;
  }

  public Instant getSlaDeadline() {
    return slaDeadline;
  }

  public void setSlaDeadline(Instant slaDeadline) {
    this.slaDeadline = slaDeadline;
  }

  public Boolean getExceededSla() {
    return exceededSla;
  }

  public void setExceededSla(Boolean exceededSla) {
    this.exceededSla = exceededSla;
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

  public Company getCompany() {
    return company;
  }

  public void setCompany(Company company) {
    this.company = company;
  }

  public ReportSchedule getSchedule() {
    return schedule;
  }

  public void setSchedule(ReportSchedule schedule) {
    this.schedule = schedule;
  }

  public AccountingPeriod getPeriod() {
    return period;
  }

  public void setPeriod(AccountingPeriod period) {
    this.period = period;
  }

  public User getTriggeredBy() {
    return triggeredBy;
  }

  public void setTriggeredBy(User triggeredBy) {
    this.triggeredBy = triggeredBy;
  }

  public ReportScheduleRun getRerunOfRun() {
    return rerunOfRun;
  }

  public void setRerunOfRun(ReportScheduleRun rerunOfRun) {
    this.rerunOfRun = rerunOfRun;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ReportScheduleRun that = (ReportScheduleRun) o;
    return Objects.equals(id, that.id);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }

  @Override
  public String toString() {
    return "ReportScheduleRun{" +
        "id=" + id +
        ", companyId=" + companyId +
        ", scheduleId=" + scheduleId +
        ", status=" + status +
        ", triggerType=" + triggerType +
        ", attempt=" + attempt +
        '}';
  }
}
