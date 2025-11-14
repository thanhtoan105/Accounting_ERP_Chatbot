package com.accounting.dto.audit;

import java.time.Instant;

public record AuditLogFilter(
    Long companyId,
    String entityType,
    String entityId,
    String action,
    String eventType,
    String userEmail,
    String actorRole,
    Boolean success,
    Instant from,
    Instant to,
    int page,
    int size) {}



