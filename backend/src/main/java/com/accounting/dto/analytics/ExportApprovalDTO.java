package com.accounting.dto.analytics;

import java.time.Instant;

import com.accounting.entity.analytics.ExportApprovalStatus;

public record ExportApprovalDTO(
        Long id,
        Long companyId,
        Long requesterId,
        String requesterName,
        String requesterEmail,
        String exportType,
        String queryParameters,
        Integer rowCount,
        ExportApprovalStatus status,
        Long approverId,
        String approverName,
        String rejectionReason,
        Instant requestedAt,
        Instant decidedAt,
        String downloadUrl,
        Instant downloadExpiry,
        Integer downloadCount,
        Instant createdAt) {}
