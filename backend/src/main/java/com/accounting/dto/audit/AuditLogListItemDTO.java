package com.accounting.dto.audit;

import java.time.Instant;
import java.util.Map;

public record AuditLogListItemDTO(
    Long id,
    Instant occurredAt,
    AuditActorDTO actor,
    String action,
    String eventType,
    Boolean success,
    String failureReason,
    String entityType,
    String entityId,
    String entityDisplay,
    Map<String, Object> changes,
    Map<String, Object> metadata,
    String ipAddress,
    String userAgent,
    String traceId) {}
