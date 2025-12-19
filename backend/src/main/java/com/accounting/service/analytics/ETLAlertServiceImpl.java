package com.accounting.service.analytics;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.accounting.dto.analytics.DashboardReconciliationReport;
import com.accounting.entity.Company;
import com.accounting.entity.dashboard.DashboardETLRun;
import com.accounting.entity.dashboard.ETLJobStatus;
import com.accounting.repository.CompanyRepository;
import com.accounting.repository.dashboard.DashboardETLRunRepository;

@Service
public class ETLAlertServiceImpl implements ETLAlertService {

    private static final Logger logger = LoggerFactory.getLogger(ETLAlertServiceImpl.class);

    private static final Duration DEFAULT_STALENESS_THRESHOLD = Duration.ofMinutes(30);
    private static final int FAILURE_THRESHOLD_FOR_ALERT = 3;
    private static final Duration STUCK_JOB_THRESHOLD = Duration.ofMinutes(60);

    private final DashboardETLRunRepository etlRunRepository;
    private final CompanyRepository companyRepository;

    @Value("${etl.alert.staleness-threshold-minutes:30}")
    private long stalenessThresholdMinutes;

    @Value("${etl.alert.failure-threshold:3}")
    private int failureThreshold;

    @Value("${etl.alert.enabled:true}")
    private boolean alertsEnabled;

    public ETLAlertServiceImpl(
            DashboardETLRunRepository etlRunRepository,
            CompanyRepository companyRepository) {
        this.etlRunRepository = etlRunRepository;
        this.companyRepository = companyRepository;
    }

    @Override
    public void alertOnJobFailure(DashboardETLRun run) {
        if (!alertsEnabled || run == null) {
            return;
        }

        if (run.getStatus() != ETLJobStatus.FAILED) {
            logger.debug("ETL run {} is not in FAILED status, skipping alert", run.getId());
            return;
        }

        ETLAlert alert = new ETLAlert(
                ETLAlertType.JOB_FAILURE,
                run.getCompanyId(),
                String.format("ETL job '%s' failed", run.getJobName()),
                run.getErrorMessage(),
                determineFailureSeverity(run.getCompanyId()));

        sendAlert(alert);

        checkForRepeatedFailures(run.getCompanyId());
    }

    @Override
    public void alertOnStaleness(Long companyId, Duration staleDuration) {
        if (!alertsEnabled || companyId == null) {
            return;
        }

        Duration threshold = Duration.ofMinutes(stalenessThresholdMinutes);
        if (staleDuration.compareTo(threshold) < 0) {
            logger.debug("Data for company {} is not stale enough ({}), threshold: {}",
                    companyId, staleDuration, threshold);
            return;
        }

        ETLAlertSeverity severity = staleDuration.compareTo(Duration.ofHours(1)) > 0
                ? ETLAlertSeverity.CRITICAL
                : ETLAlertSeverity.WARNING;

        ETLAlert alert = new ETLAlert(
                ETLAlertType.DATA_STALENESS,
                companyId,
                String.format("Dashboard data is stale for company %d", companyId),
                String.format("Data has not been refreshed for %d minutes", staleDuration.toMinutes()),
                severity);

        sendAlert(alert);
    }

    @Override
    public void alertOnReconciliationFailure(Long companyId, UUID periodId, DashboardReconciliationReport report) {
        if (!alertsEnabled || companyId == null || report == null) {
            return;
        }

        String failedChecks = report.results().stream()
                .filter(r -> !r.passed())
                .map(r -> String.format("%s (MV: %s, GL: %s, variance: %s)",
                        r.checkType(), r.mvTotal(), r.glTotal(), r.variance()))
                .toList()
                .toString();

        ETLAlert alert = new ETLAlert(
                ETLAlertType.ANOMALY_DETECTED,
                companyId,
                String.format("Dashboard reconciliation failed for company %d period %s", companyId, periodId),
                String.format("Failed checks: %s. TT200 compliance requires MV totals to match GL within 1 VND tolerance.", failedChecks),
                ETLAlertSeverity.WARNING);

        sendAlert(alert);
    }

    @Override
    @Scheduled(fixedRateString = "${etl.alert.anomaly-check-interval-ms:300000}")
    public void checkAndAlertAnomalies() {
        if (!alertsEnabled) {
            logger.debug("ETL alerts are disabled, skipping anomaly check");
            return;
        }

        logger.info("Running scheduled ETL anomaly check");
        AnomalyCheckResult result = performAnomalyCheck();

        if (result.hasAnomalies()) {
            logger.warn("Detected {} ETL anomalies", result.alerts().size());
            result.alerts().forEach(this::sendAlert);
        } else {
            logger.debug("No ETL anomalies detected");
        }
    }

    private AnomalyCheckResult performAnomalyCheck() {
        List<ETLAlert> alerts = new ArrayList<>();

        alerts.addAll(checkForStuckJobs());
        alerts.addAll(checkForStaleData());
        alerts.addAll(checkForHighFailureRates());

        return AnomalyCheckResult.withAlerts(alerts);
    }

    private List<ETLAlert> checkForStuckJobs() {
        List<ETLAlert> alerts = new ArrayList<>();

        Instant staleThreshold = Instant.now().minus(STUCK_JOB_THRESHOLD);
        List<DashboardETLRun> stuckJobs = etlRunRepository.findStaleRunningJobs(staleThreshold);

        for (DashboardETLRun job : stuckJobs) {
            ETLAlert alert = new ETLAlert(
                    ETLAlertType.STUCK_JOB,
                    job.getCompanyId(),
                    String.format("ETL job '%s' appears to be stuck", job.getJobName()),
                    String.format("Job started at %s and has been running for over %d minutes",
                            job.getStartedAt(), STUCK_JOB_THRESHOLD.toMinutes()),
                    ETLAlertSeverity.WARNING);
            alerts.add(alert);
        }

        return alerts;
    }

    private List<ETLAlert> checkForStaleData() {
        List<ETLAlert> alerts = new ArrayList<>();

        List<Company> companies = companyRepository.findAll();
        Instant stalenessThreshold = Instant.now().minus(Duration.ofMinutes(stalenessThresholdMinutes));

        for (Company company : companies) {
            Optional<DashboardETLRun> latestSuccessful = etlRunRepository
                    .findLatestByCompanyIdAndStatus(company.getId(), ETLJobStatus.COMPLETED);

            if (latestSuccessful.isEmpty()) {
                ETLAlert alert = new ETLAlert(
                        ETLAlertType.DATA_STALENESS,
                        company.getId(),
                        "No successful ETL run found for company " + company.getId(),
                        "Dashboard data may be unavailable",
                        ETLAlertSeverity.WARNING);
                alerts.add(alert);
                continue;
            }

            DashboardETLRun run = latestSuccessful.get();
            if (run.getCompletedAt() != null && run.getCompletedAt().isBefore(stalenessThreshold)) {
                Duration staleDuration = Duration.between(run.getCompletedAt(), Instant.now());
                ETLAlert alert = new ETLAlert(
                        ETLAlertType.DATA_STALENESS,
                        company.getId(),
                        String.format("Dashboard data is stale for company %d", company.getId()),
                        String.format("Last successful refresh was %d minutes ago", staleDuration.toMinutes()),
                        staleDuration.compareTo(Duration.ofHours(1)) > 0
                                ? ETLAlertSeverity.CRITICAL
                                : ETLAlertSeverity.WARNING);
                alerts.add(alert);
            }
        }

        return alerts;
    }

    private List<ETLAlert> checkForHighFailureRates() {
        List<ETLAlert> alerts = new ArrayList<>();

        List<Company> companies = companyRepository.findAll();
        Instant since = Instant.now().minus(Duration.ofHours(1));

        for (Company company : companies) {
            long failedCount = etlRunRepository.countFailedSince(company.getId(), since);

            if (failedCount >= failureThreshold) {
                ETLAlert alert = new ETLAlert(
                        ETLAlertType.REPEATED_FAILURES,
                        company.getId(),
                        String.format("High ETL failure rate for company %d", company.getId()),
                        String.format("%d failures in the last hour (threshold: %d)",
                                failedCount, failureThreshold),
                        ETLAlertSeverity.CRITICAL);
                alerts.add(alert);
            }
        }

        return alerts;
    }

    private void checkForRepeatedFailures(Long companyId) {
        Instant since = Instant.now().minus(Duration.ofHours(1));
        long failedCount = etlRunRepository.countFailedSince(companyId, since);

        if (failedCount >= FAILURE_THRESHOLD_FOR_ALERT) {
            ETLAlert alert = new ETLAlert(
                    ETLAlertType.REPEATED_FAILURES,
                    companyId,
                    String.format("Repeated ETL failures for company %d", companyId),
                    String.format("%d failures in the last hour", failedCount),
                    ETLAlertSeverity.CRITICAL);
            sendAlert(alert);
        }
    }

    private ETLAlertSeverity determineFailureSeverity(Long companyId) {
        Instant since = Instant.now().minus(Duration.ofHours(1));
        long recentFailures = etlRunRepository.countFailedSince(companyId, since);

        if (recentFailures >= FAILURE_THRESHOLD_FOR_ALERT) {
            return ETLAlertSeverity.CRITICAL;
        } else if (recentFailures >= 2) {
            return ETLAlertSeverity.WARNING;
        }
        return ETLAlertSeverity.INFO;
    }

    private void sendAlert(ETLAlert alert) {
        switch (alert.severity()) {
            case CRITICAL -> logger.error("[ETL ALERT - {}] Company {}: {} - {}",
                    alert.type(), alert.companyId(), alert.message(), alert.details());
            case WARNING -> logger.warn("[ETL ALERT - {}] Company {}: {} - {}",
                    alert.type(), alert.companyId(), alert.message(), alert.details());
            case INFO -> logger.info("[ETL ALERT - {}] Company {}: {} - {}",
                    alert.type(), alert.companyId(), alert.message(), alert.details());
        }
    }
}
