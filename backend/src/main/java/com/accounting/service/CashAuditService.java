package com.accounting.service;

import com.accounting.dto.audit.CashAuditPageDTO;
import com.accounting.dto.audit.CashAuditQueryDTO;
import com.accounting.dto.audit.PurgeRequestDTO;
import com.accounting.dto.audit.PurgeResponseDTO;
import java.util.UUID;

/**
 * Service interface for Cash & Bank audit operations.
 *
 * <p>Provides audit log querying, export, and purge workflow functionality
 * for the Cash & Bank module compliance requirements (Story 6.6).
 *
 * <p><b>Multi-Tenancy:</b> All methods require company context via
 * {@link com.accounting.security.CompanyContext#getCompanyId()}.
 *
 * <p><b>Role Requirements:</b>
 * <ul>
 *   <li>Query/Export: ADMIN or CHIEF_ACCOUNTANT</li>
 *   <li>Purge Request: CHIEF_ACCOUNTANT</li>
 *   <li>Purge Approve/Reject: ADMIN (must be different from requester)</li>
 * </ul>
 *
 * @see com.accounting.enums.CashBankAuditAction
 */
public interface CashAuditService {

  /**
   * Query audit logs with filters.
   *
   * <p>Validates:
   * <ul>
   *   <li>Company context is present</li>
   *   <li>Date range is ≤ 12 months</li>
   *   <li>Page size is 10, 20, or 50</li>
   * </ul>
   *
   * <p>Logs: {@code AUDIT_EXPLORER_QUERY} action
   *
   * @param filter the query filter criteria
   * @return paginated audit log results
   * @throws org.springframework.web.server.ResponseStatusException if company context missing
   *         or validation fails
   */
  CashAuditPageDTO queryAuditLogs(CashAuditQueryDTO filter);

  /**
   * Export audit logs to specified format.
   *
   * <p>Export includes:
   * <ul>
   *   <li>SHA-256 hash in document footer</li>
   *   <li>Period status badge (OPEN/CLOSED)</li>
   *   <li>"DRAFT" watermark if period is open (PDF only)</li>
   * </ul>
   *
   * <p>Logs: {@code COMPLIANCE_EXPORT} action
   *
   * @param filter the query filter criteria
   * @param format export format (JSON, CSV, or PDF)
   * @return byte array of exported content
   * @throws org.springframework.web.server.ResponseStatusException if export limit exceeded
   *         (10,000 records)
   */
  byte[] exportAuditLogs(CashAuditQueryDTO filter, ExportFormat format);

  /**
   * Get the content hash for the last export operation.
   *
   * @return SHA-256 hash of last export, or null if no export performed
   */
  String getLastExportHash();

  /**
   * Request audit log purge for compliance (e.g., GDPR).
   *
   * <p>Creates a purge request that requires Admin approval.
   * Dual approval is enforced: approver must be different from requester.
   *
   * <p>Logs: {@code PURGE_REQUEST} action
   *
   * @param request the purge request details
   * @return purge request response with ID and status
   */
  PurgeResponseDTO requestPurge(PurgeRequestDTO request);

  /**
   * Approve a pending purge request and execute the purge.
   *
   * <p>Validates:
   * <ul>
   *   <li>Request exists and is pending</li>
   *   <li>Current user (approver) is different from requester</li>
   *   <li>Current user has ADMIN role</li>
   * </ul>
   *
   * <p>Logs: {@code PURGE_APPROVE} action
   *
   * @param requestId the purge request ID
   * @return purge response with records purged count
   * @throws com.accounting.exception.BusinessException with code SELF_APPROVAL_FORBIDDEN
   *         if approver is same as requester
   * @throws org.springframework.web.server.ResponseStatusException if request not found
   */
  PurgeResponseDTO approvePurge(UUID requestId);

  /**
   * Reject a pending purge request.
   *
   * <p>Logs: {@code PURGE_REJECT} action
   *
   * @param requestId the purge request ID
   * @param reason the rejection reason
   * @return purge response with rejected status
   * @throws org.springframework.web.server.ResponseStatusException if request not found
   */
  PurgeResponseDTO rejectPurge(UUID requestId, String reason);

  /**
   * Get a specific purge request by ID.
   *
   * @param requestId the purge request ID
   * @return purge response details, or null if not found
   */
  PurgeResponseDTO getPurgeRequest(UUID requestId);

  /**
   * Export format options for audit logs.
   */
  enum ExportFormat {
    JSON,
    CSV,
    PDF
  }
}
