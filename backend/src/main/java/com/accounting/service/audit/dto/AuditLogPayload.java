package com.accounting.service.audit.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AuditLogPayload {

    private final Long companyId;
    private final Instant eventTime;
    private final String eventType;
    private final String eventSubtype;
    private final Long principalId;
    private final String principalType;
    private final String objectType;
    private final String objectId;
    private final String ipAddress;
    private final String userAgent;
    private final UUID requestId;
    private final String requestPath;
    private final String requestMethod;
    private final Integer responseStatus;
    private final Map<String, Object> metadata;
}
