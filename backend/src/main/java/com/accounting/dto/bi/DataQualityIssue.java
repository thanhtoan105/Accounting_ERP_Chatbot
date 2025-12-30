package com.accounting.dto.bi;

import java.time.Instant;
import java.util.UUID;

public record DataQualityIssue(
        String checkName,
        Severity severity,
        String description,
        String affectedEntity,
        UUID affectedId,
        Instant detectedAt) {

    public enum Severity {
        CRITICAL,
        WARNING,
        INFO
    }

    public static DataQualityIssue critical(
            String checkName, String description, String entity, UUID id) {
        return new DataQualityIssue(
                checkName, Severity.CRITICAL, description, entity, id, Instant.now());
    }

    public static DataQualityIssue warning(
            String checkName, String description, String entity, UUID id) {
        return new DataQualityIssue(
                checkName, Severity.WARNING, description, entity, id, Instant.now());
    }

    public static DataQualityIssue info(String checkName, String description, String entity) {
        return new DataQualityIssue(
                checkName, Severity.INFO, description, entity, null, Instant.now());
    }
}
