package com.accounting.controller.audit.dto;

import java.time.Instant;
import java.time.LocalDate;

public record AuditCheckpointResponse(
    Long companyId,
    LocalDate eventDateUtc,
    Long firstSequence,
    Long lastSequence,
    Long recordCount,
    String merkleRoot,
    String chainHeadHash,
    String chainTailHash,
    String status,
    Instant lastVerifiedAt
) {}
