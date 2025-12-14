package com.accounting.service.analytics;

import java.util.UUID;

import com.accounting.entity.dashboard.DashboardETLRun;

public interface ETLPipelineService {

    DashboardETLRun refreshMaterializedViews(Long companyId);

    DashboardETLRun refreshMaterializedViewsManual(Long companyId, Long triggeredByUserId);

    IntegrityCheckResult runIntegrityChecks(Long companyId);

    void updateFreshnessStatus(Long companyId, UUID etlRunId, boolean success);

    DashboardETLRun getETLRunStatus(UUID jobId);

    boolean acquireLock(Long companyId, String instanceId);

    void releaseLock(Long companyId, String instanceId);

    record IntegrityCheckResult(
            boolean passed,
            boolean debitCreditBalanced,
            int orphanCount,
            String errorMessage) {

        public static IntegrityCheckResult success() {
            return new IntegrityCheckResult(true, true, 0, null);
        }

        public static IntegrityCheckResult failure(String message) {
            return new IntegrityCheckResult(false, false, 0, message);
        }

        public static IntegrityCheckResult withOrphans(int count) {
            return new IntegrityCheckResult(count == 0, true, count, 
                count > 0 ? "Found " + count + " orphaned entries" : null);
        }
    }
}
