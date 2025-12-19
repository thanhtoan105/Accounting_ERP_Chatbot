package com.accounting.dto.analytics;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record DashboardReconciliationReport(
        Long companyId,
        UUID periodId,
        LocalDateTime checkTime,
        List<ReconciliationResult> results,
        boolean allPassed,
        String overallStatus) {

    public static DashboardReconciliationReport of(Long companyId, UUID periodId,
            List<ReconciliationResult> results) {
        boolean allPassed = results.stream().allMatch(ReconciliationResult::passed);
        boolean anyFailed = results.stream().anyMatch(r -> !r.passed());
        String status = allPassed ? "PASSED" : (anyFailed ? "FAILED" : "WARNING");
        return new DashboardReconciliationReport(
                companyId,
                periodId,
                LocalDateTime.now(),
                results,
                allPassed,
                status);
    }
}
