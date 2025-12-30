package com.accounting.dto.bi;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public record DataQualitySummary(
        int totalIssues,
        int criticalCount,
        int warningCount,
        int infoCount,
        Map<String, Integer> issuesByCheck,
        Instant checkedAt,
        String overallStatus) {

    public static DataQualitySummary from(List<DataQualityIssue> issues) {
        int critical =
                (int) issues.stream()
                        .filter(i -> i.severity() == DataQualityIssue.Severity.CRITICAL)
                        .count();
        int warning =
                (int) issues.stream()
                        .filter(i -> i.severity() == DataQualityIssue.Severity.WARNING)
                        .count();
        int info =
                (int) issues.stream()
                        .filter(i -> i.severity() == DataQualityIssue.Severity.INFO)
                        .count();

        Map<String, Integer> byCheck =
                issues.stream()
                        .collect(Collectors.groupingBy(DataQualityIssue::checkName))
                        .entrySet()
                        .stream()
                        .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().size()));

        String status;
        if (critical > 0) {
            status = "CRITICAL";
        } else if (warning > 0) {
            status = "WARNING";
        } else if (info > 0) {
            status = "INFO";
        } else {
            status = "HEALTHY";
        }

        return new DataQualitySummary(
                issues.size(), critical, warning, info, byCheck, Instant.now(), status);
    }
}
