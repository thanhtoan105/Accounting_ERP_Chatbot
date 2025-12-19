package com.accounting.service.audit;

import java.time.LocalDate;
import java.util.List;

import com.accounting.entity.analytics.AnalyticsAuditLog;
import com.accounting.service.audit.dto.AuditChainVerificationResult;
import com.accounting.service.audit.dto.AuditLogPayload;

public interface AuditHashChainService {

    AnalyticsAuditLog appendLog(AuditLogPayload payload);

    AuditChainVerificationResult verifyDailyChain(Long companyId, LocalDate eventDateUtc);

    void recomputeAndStoreDailyCheckpoint(Long companyId, LocalDate eventDateUtc);

    String computeMerkleRoot(List<String> leafHashes);
}
