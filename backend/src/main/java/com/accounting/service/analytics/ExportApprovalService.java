package com.accounting.service.analytics;

import java.util.List;

import com.accounting.dto.analytics.ExportApprovalDTO;
import com.accounting.entity.analytics.ExportApprovalRequest;

public interface ExportApprovalService {

    ExportApprovalRequest requestLargeExport(String exportType, Integer rowCount, String queryParameters);

    ExportApprovalDTO approve(Long requestId);

    ExportApprovalDTO reject(Long requestId, String reason);

    ExportApprovalDTO getById(Long requestId);

    List<ExportApprovalDTO> getPendingExports();

    List<ExportApprovalDTO> getMyExportRequests();

    ExportApprovalRequest getByDownloadToken(String token);

    byte[] downloadExport(String token);

    void markAsDownloaded(Long requestId);

    int cleanupExpired();

    boolean requiresApproval(int rowCount);

    int getLargeExportThreshold();
}
