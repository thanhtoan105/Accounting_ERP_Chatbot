package com.accounting.entity.dashboard;

import java.time.Instant;
import java.util.UUID;

import com.accounting.repository.CompanyScopedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;

@Entity
@Table(name = "dashboard_freshness")
public class DashboardFreshness implements CompanyScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull
    @Column(name = "company_id", nullable = false, unique = true)
    private Long companyId;

    @Column(name = "last_successful_refresh")
    private Instant lastSuccessfulRefresh;

    @Column(name = "last_refresh_attempt")
    private Instant lastRefreshAttempt;

    @Column(name = "last_refresh_status", length = 20)
    private String lastRefreshStatus;

    @Column(name = "consecutive_failures")
    private Integer consecutiveFailures = 0;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "freshness_level", nullable = false, length = 10)
    private FreshnessLevel freshnessLevel = FreshnessLevel.RED;

    @Column(name = "last_etl_run_id")
    private UUID lastEtlRunId;

    @Column(name = "data_as_of_timestamp")
    private Instant dataAsOfTimestamp;

    @Column(name = "last_posted_voucher_id")
    private UUID lastPostedVoucherId;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public void updateFreshnessLevel() {
        if (lastSuccessfulRefresh == null) {
            this.freshnessLevel = FreshnessLevel.RED;
            return;
        }

        long minutesSinceRefresh = java.time.Duration.between(lastSuccessfulRefresh, Instant.now()).toMinutes();

        if (minutesSinceRefresh < 5) {
            this.freshnessLevel = FreshnessLevel.GREEN;
        } else if (minutesSinceRefresh <= 30) {
            this.freshnessLevel = FreshnessLevel.YELLOW;
        } else {
            this.freshnessLevel = FreshnessLevel.RED;
        }
    }

    public void recordSuccess(UUID etlRunId, Instant dataAsOf) {
        Instant now = Instant.now();
        this.lastSuccessfulRefresh = now;
        this.lastRefreshAttempt = now;
        this.lastRefreshStatus = "COMPLETED";
        this.consecutiveFailures = 0;
        this.lastEtlRunId = etlRunId;
        this.dataAsOfTimestamp = dataAsOf;
        updateFreshnessLevel();
    }

    public void recordFailure() {
        this.lastRefreshAttempt = Instant.now();
        this.lastRefreshStatus = "FAILED";
        this.consecutiveFailures++;
        updateFreshnessLevel();
    }

    @Override
    public Long getCompanyId() {
        return companyId;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public void setCompanyId(Long companyId) {
        this.companyId = companyId;
    }

    public Instant getLastSuccessfulRefresh() {
        return lastSuccessfulRefresh;
    }

    public void setLastSuccessfulRefresh(Instant lastSuccessfulRefresh) {
        this.lastSuccessfulRefresh = lastSuccessfulRefresh;
    }

    public Instant getLastRefreshAttempt() {
        return lastRefreshAttempt;
    }

    public void setLastRefreshAttempt(Instant lastRefreshAttempt) {
        this.lastRefreshAttempt = lastRefreshAttempt;
    }

    public String getLastRefreshStatus() {
        return lastRefreshStatus;
    }

    public void setLastRefreshStatus(String lastRefreshStatus) {
        this.lastRefreshStatus = lastRefreshStatus;
    }

    public Integer getConsecutiveFailures() {
        return consecutiveFailures;
    }

    public void setConsecutiveFailures(Integer consecutiveFailures) {
        this.consecutiveFailures = consecutiveFailures;
    }

    public FreshnessLevel getFreshnessLevel() {
        return freshnessLevel;
    }

    public void setFreshnessLevel(FreshnessLevel freshnessLevel) {
        this.freshnessLevel = freshnessLevel;
    }

    public UUID getLastEtlRunId() {
        return lastEtlRunId;
    }

    public void setLastEtlRunId(UUID lastEtlRunId) {
        this.lastEtlRunId = lastEtlRunId;
    }

    public Instant getDataAsOfTimestamp() {
        return dataAsOfTimestamp;
    }

    public void setDataAsOfTimestamp(Instant dataAsOfTimestamp) {
        this.dataAsOfTimestamp = dataAsOfTimestamp;
    }

    public UUID getLastPostedVoucherId() {
        return lastPostedVoucherId;
    }

    public void setLastPostedVoucherId(UUID lastPostedVoucherId) {
        this.lastPostedVoucherId = lastPostedVoucherId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
