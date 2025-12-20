package com.accounting.controller.audit.dto;

import java.time.LocalDate;

public record VerifyDailyChainResponse(
    Long companyId,
    LocalDate eventDateUtc,
    boolean verified,
    String status,
    String mismatchReason,
    boolean checkpointCreated
) {}
