package com.accounting.service;

import com.accounting.dto.audit.CashAuditQueryDTO;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Service interface for compliance-controlled exports with security features.
 *
 * <p>Features:
 * <ul>
 *   <li>SHA-256 hash in document footer</li>
 *   <li>"DRAFT" watermark for open period exports</li>
 *   <li>Signature block template</li>
 *   <li>Weekly backup archive creation</li>
 * </ul>
 *
 * @see com.accounting.enums.CashBankAuditAction#BACKUP_EXPORT
 * @see com.accounting.enums.CashBankAuditAction#COMPLIANCE_EXPORT
 */
public interface ComplianceExportService {

  /**
   * Export audit logs with compliance controls.
   *
   * <p>Adds:
   * <ul>
   *   <li>Period status badge (OPEN/CLOSED)</li>
   *   <li>"DRAFT" watermark if period is open</li>
   *   <li>SHA-256 hash in footer</li>
   *   <li>Signature block template</li>
   * </ul>
   *
   * @param filter query filter criteria
   * @param format export format (JSON, CSV, PDF)
   * @return export result with content, hash, and metadata
   */
  ComplianceExportResult exportAuditLogs(CashAuditQueryDTO filter, ExportFormat format);

  /**
   * Create weekly backup archive for a company.
   *
   * <p>Contents:
   * <ul>
   *   <li>Bank accounts master data (JSON)</li>
   *   <li>Cash book entries for period (JSON)</li>
   *   <li>Reconciliation sessions and matches (JSON)</li>
   *   <li>Audit logs for period (JSON)</li>
   *   <li>Manifest file with SHA-256 hashes</li>
   * </ul>
   *
   * @param companyId the company ID
   * @param fromDate start date for data export
   * @param toDate end date for data export
   * @return backup result with archive URL and metadata
   */
  BackupResult createWeeklyBackup(Long companyId, LocalDate fromDate, LocalDate toDate);

  /**
   * Calculate SHA-256 hash of document content.
   *
   * @param content document bytes
   * @return hex-encoded SHA-256 hash
   */
  String calculateDocumentHash(byte[] content);

  /**
   * Check if a period is open for a given date.
   *
   * @param date the date to check
   * @return true if period is open
   */
  boolean isPeriodOpen(LocalDate date);

  /**
   * Get period status display for a date range.
   *
   * @param fromDate start date
   * @param toDate end date
   * @return period status info
   */
  PeriodStatusInfo getPeriodStatus(LocalDate fromDate, LocalDate toDate);

  // === Inner Classes ===

  enum ExportFormat {
    JSON,
    CSV,
    PDF
  }

  record ComplianceExportResult(
      byte[] content,
      String filename,
      String hash,
      int recordCount,
      String mimeType,
      boolean isDraft,
      String periodStatus) {}

  record BackupResult(
      UUID backupId,
      String archiveUrl,
      long fileSize,
      int fileCount,
      String manifestHash,
      boolean uploaded) {}

  record PeriodStatusInfo(
      String periodName,
      LocalDate startDate,
      LocalDate endDate,
      boolean isOpen,
      UUID periodId) {}
}
