package com.accounting.service.audit.dto;

import java.time.LocalDate;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AuditChainVerificationResult {

    private final Long companyId;
    private final LocalDate eventDateUtc;
    private final boolean verified;
    private final boolean checkpointCreated;
    private final String status;
    private final String mismatchReason;

    public static final String STATUS_OK = "OK";
    public static final String STATUS_MISMATCH = "MISMATCH";
    public static final String STATUS_MISSING = "MISSING";
}
