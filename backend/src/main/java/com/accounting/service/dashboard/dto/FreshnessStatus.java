package com.accounting.service.dashboard.dto;

import java.time.Duration;
import java.time.Instant;

import com.accounting.entity.dashboard.FreshnessLevel;

public record FreshnessStatus(
        FreshnessLevel level,
        Instant lastSuccessfulRefresh,
        Duration timeSinceRefresh,
        String message,
        Integer consecutiveFailures,
        String datasetType) {

    public static FreshnessStatus noData() {
        return new FreshnessStatus(
                FreshnessLevel.RED,
                null,
                null,
                "No data available. Dashboard has never been refreshed.",
                0,
                null);
    }

    public static FreshnessStatus noDataForDataset(String datasetType) {
        return new FreshnessStatus(
                FreshnessLevel.RED,
                null,
                null,
                "No data available for dataset: " + datasetType,
                0,
                datasetType);
    }

    public static FreshnessStatus of(
            FreshnessLevel level,
            Instant lastSuccessfulRefresh,
            Integer consecutiveFailures,
            String datasetType) {
        Duration timeSinceRefresh = lastSuccessfulRefresh != null
                ? Duration.between(lastSuccessfulRefresh, Instant.now())
                : null;
        return new FreshnessStatus(
                level,
                lastSuccessfulRefresh,
                timeSinceRefresh,
                buildMessage(level, timeSinceRefresh),
                consecutiveFailures,
                datasetType);
    }

    private static String buildMessage(FreshnessLevel level, Duration timeSinceRefresh) {
        if (timeSinceRefresh == null) {
            return "No refresh data available";
        }

        long minutes = timeSinceRefresh.toMinutes();
        return switch (level) {
            case GREEN -> "Data is fresh (last updated " + minutes + " minutes ago)";
            case YELLOW -> "Data is slightly stale (" + minutes + " minutes since last refresh)";
            case RED -> "Data is stale - refresh recommended (" + minutes + " minutes since last refresh)";
        };
    }
}
