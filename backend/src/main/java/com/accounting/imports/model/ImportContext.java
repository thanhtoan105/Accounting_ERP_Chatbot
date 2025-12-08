package com.accounting.imports.model;

import java.time.Instant;
import java.util.UUID;

public record ImportContext(
        long companyId,
        long userId,
        String filename,
        Instant requestedAt,
        String locale,
        UUID attemptId,
        String ipAddress,
        String userAgent) {
}
