package com.accounting.service;

import java.time.LocalDate;

import com.accounting.service.impl.audit.AuditChainVerificationResult;

public interface AuditHashChainService {

    AuditChainVerificationResult verifyDailyChain(Long companyId, LocalDate date);

    void recomputeAndStoreDailyCheckpoint(Long companyId, LocalDate date);
}
