package com.accounting.entity.report;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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
import jakarta.persistence.Version;

@Entity
@Table(name = "report_schedules")
public class ReportSchedule implements CompanyScopedEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "company_id", nullable = false)
  private Long companyId;

  @Column(name = "name", nullable = false, length = 255)
  private String name;

  @Column(name = "report_type", nullable = false, length = 20)
  private String reportType;

  @Column(name = "cron_expression", nullable = false, length = 100)
  private String cronExpression;

  @Enumerated(EnumType.STRING)
  @Column(name = "period_rule", nullable = false, length = 20)
  private PeriodRule periodRule;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "parameters", columnDefinition = "jsonb")
  private String parameters;

  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(name = "export_formats", columnDefinition = "text[]")
  private List<String> exportFormats;

  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(name = "recipients", columnDefinition = "text[]")
  private List<String> recipients;

  @Column(name = "owner_id", nullable = false)
  private Long ownerId;

  @Column(name = "is_active", nullable = false)
  private Boolean isActive = true;

  @Column(name = "last_run_at")
  private Instant lastRunAt;

  @Column(name = "next_run_at")
  private Instant nextRunAt;

  @Version
  @Column(name = "version", nullable = false)
  private Integer version = 0;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "company_id", insertable = false, updatable = false)
  private Company company;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "owner_id", insertable = false, updatable = false)
  private User owner;

  @PrePersist
  protected void onCreate() {
    createdAt = Instant.now();
    updatedAt = Instant.now();
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

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getReportType() {
    return reportType;
  }

  public void setReportType(String reportType) {
    this.reportType = reportType;
  }

  public String getCronExpression() {
    return cronExpression;
  }

  public void setCronExpression(String cronExpression) {
    this.cronExpression = cronExpression;
  }

  public PeriodRule getPeriodRule() {
    return periodRule;
  }

  public void setPeriodRule(PeriodRule periodRule) {
    this.periodRule = periodRule;
  }

  public String getParameters() {
    return parameters;
  }

  public void setParameters(String parameters) {
    this.parameters = parameters;
  }

  public List<String> getExportFormats() {
    return exportFormats;
  }

  public void setExportFormats(List<String> exportFormats) {
    this.exportFormats = exportFormats;
  }

  public List<String> getRecipients() {
    return recipients;
  }

  public void setRecipients(List<String> recipients) {
    this.recipients = recipients;
  }

  public Long getOwnerId() {
    return ownerId;
  }

  public void setOwnerId(Long ownerId) {
    this.ownerId = ownerId;
  }

  public Boolean getIsActive() {
    return isActive;
  }

  public void setIsActive(Boolean isActive) {
    this.isActive = isActive;
  }

  public Instant getLastRunAt() {
    return lastRunAt;
  }

  public void setLastRunAt(Instant lastRunAt) {
    this.lastRunAt = lastRunAt;
  }

  public Instant getNextRunAt() {
    return nextRunAt;
  }

  public void setNextRunAt(Instant nextRunAt) {
    this.nextRunAt = nextRunAt;
  }

  public Integer getVersion() {
    return version;
  }

  public void setVersion(Integer version) {
    this.version = version;
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

  public User getOwner() {
    return owner;
  }

  public void setOwner(User owner) {
    this.owner = owner;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ReportSchedule that = (ReportSchedule) o;
    return Objects.equals(id, that.id);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }

  @Override
  public String toString() {
    return "ReportSchedule{" +
        "id=" + id +
        ", companyId=" + companyId +
        ", name='" + name + '\'' +
        ", reportType='" + reportType + '\'' +
        ", periodRule=" + periodRule +
        ", isActive=" + isActive +
        '}';
  }
}
