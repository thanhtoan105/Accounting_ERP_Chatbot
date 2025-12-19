package com.accounting.dto.analytics;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record ReconciliationReportDTO(
        Long companyId,
        String companyName,
        UUID periodId,
        String periodName,
        LocalDateTime generatedAt,
        List<ReconciliationLineDTO> lines,
        String overallStatus) {

    public record ReconciliationLineDTO(
            String category,
            BigDecimal mvTotal,
            BigDecimal glTotal,
            BigDecimal variance,
            String status) {

        public static ReconciliationLineDTO from(ReconciliationResult result) {
            return new ReconciliationLineDTO(
                    result.checkType(),
                    result.mvTotal(),
                    result.glTotal(),
                    result.variance(),
                    result.passed() ? "PASSED" : "FAILED");
        }
    }

    public static ReconciliationReportDTO from(
            DashboardReconciliationReport report,
            String companyName,
            String periodName) {
        List<ReconciliationLineDTO> lines = report.results().stream()
                .map(ReconciliationLineDTO::from)
                .toList();

        return new ReconciliationReportDTO(
                report.companyId(),
                companyName,
                report.periodId(),
                periodName,
                report.checkTime(),
                lines,
                report.overallStatus());
    }
}
