package com.accounting.service.analytics;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import com.accounting.dto.analytics.DashboardReconciliationReport;
import com.accounting.entity.dashboard.DashboardETLRun;

public interface ETLAlertService {

    void alertOnJobFailure(DashboardETLRun run);

    void alertOnStaleness(Long companyId, Duration staleDuration);
    
    void alertOnReconciliationFailure(Long companyId, UUID periodId, DashboardReconciliationReport report);

    void checkAndAlertAnomalies();

    record ETLAlert(
            ETLAlertType type,
            Long companyId,
            String message,
            String details,
            ETLAlertSeverity severity) {
    }

    enum ETLAlertType {
        JOB_FAILURE,
        DATA_STALENESS,
        ANOMALY_DETECTED,
        REPEATED_FAILURES,
        STUCK_JOB
    }

    enum ETLAlertSeverity {
        INFO,
        WARNING,
        CRITICAL
    }

    record AnomalyCheckResult(
            boolean hasAnomalies,
            List<ETLAlert> alerts) {

        public static AnomalyCheckResult none() {
            return new AnomalyCheckResult(false, List.of());
        }

        public static AnomalyCheckResult withAlerts(List<ETLAlert> alerts) {
            return new AnomalyCheckResult(!alerts.isEmpty(), alerts);
        }
    }
}
