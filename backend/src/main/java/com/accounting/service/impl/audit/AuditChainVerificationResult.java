package com.accounting.service.impl.audit;

import java.time.LocalDate;

public record AuditChainVerificationResult(
    Long companyId,
    LocalDate eventDateUtc,
    boolean verified,
    String status,
    String mismatchReason,
    boolean checkpointCreated
) {

    public static AuditChainVerificationResult success(Long companyId, LocalDate date, boolean checkpointCreated) {
        return new AuditChainVerificationResult(
            companyId, date, true, "VERIFIED", null, checkpointCreated
        );
    }

    public static AuditChainVerificationResult failure(Long companyId, LocalDate date, String reason) {
        return new AuditChainVerificationResult(
            companyId, date, false, "MISMATCH", reason, false
        );
    }

    public static AuditChainVerificationResult noData(Long companyId, LocalDate date) {
        return new AuditChainVerificationResult(
            companyId, date, true, "NO_DATA", null, false
        );
    }
}
