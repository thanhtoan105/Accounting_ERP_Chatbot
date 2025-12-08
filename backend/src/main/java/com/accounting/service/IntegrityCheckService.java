package com.accounting.service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.accounting.dto.audit.IntegrityCheckResultDTO;
import com.accounting.dto.audit.IntegrityIssueDTO;

/**
 * Service interface for integrity check operations.
 *
 * <p>Provides daily and period-close integrity checks for Cash & Bank data
 * to verify Dr/Cr parity, detect anomalies, and ensure data integrity
 * (Story 6.6 AC6.6-03).
 *
 * <p><b>Check Types:</b>
 * <ul>
 *   <li>Dr/Cr parity verification</li>
 *   <li>Duplicate transaction reference detection</li>
 *   <li>Number sequence gap detection</li>
 *   <li>Unusual amount detection (>3 std dev)</li>
 *   <li>Repeated blocked attempts detection</li>
 * </ul>
 *
 * @see com.accounting.enums.CashBankAuditAction#INTEGRITY_CHECK_PASS
 * @see com.accounting.enums.CashBankAuditAction#INTEGRITY_CHECK_FAIL
 */
public interface IntegrityCheckService {

  /**
   * Run daily integrity check for a company.
   *
   * <p>Performs the following checks:
   * <ul>
   *   <li>Dr/Cr parity for all cash/bank vouchers in the last 24 hours</li>
   *   <li>Duplicate transaction references</li>
   *   <li>Number sequence gaps</li>
   *   <li>Unusual amounts (>3 standard deviations from average)</li>
   * </ul>
   *
   * <p>Logs: {@code INTEGRITY_CHECK_PASS} or {@code INTEGRITY_CHECK_FAIL}
   *
   * @param companyId the company ID to check
   * @return check result with any issues found
   */
  IntegrityCheckResultDTO runDailyIntegrityCheck(Long companyId);

  /**
   * Run period-close integrity check for a specific accounting period.
   *
   * <p>Performs all daily checks plus:
   * <ul>
   *   <li>Reconciliation completion verification</li>
   *   <li>Pending adjustment detection</li>
   *   <li>Period-specific balance verification</li>
   * </ul>
   *
   * @param periodId the accounting period ID to verify
   * @return check result with any issues found
   */
  IntegrityCheckResultDTO runPeriodCloseCheck(UUID periodId);

  /**
   * Detect anomalies in cash/bank transactions for a date range.
   *
   * <p>Anomaly types:
   * <ul>
   *   <li>Unusual amounts: Transactions > 3 standard deviations from average</li>
   *   <li>Repeated blocked attempts: ≥3 blocked attempts per user per hour</li>
   * </ul>
   *
   * <p>Logs: {@code ANOMALY_DETECTED} for each anomaly found
   *
   * @param companyId the company ID
   * @param from start date (inclusive)
   * @param to end date (inclusive)
   * @return list of anomalies detected
   */
  List<IntegrityIssueDTO> detectAnomalies(Long companyId, LocalDate from, LocalDate to);

  /**
   * Check for repeated blocked attempts in the last hour.
   *
   * <p>Alert threshold: ≥3 blocked attempts per user per hour
   *
   * @param companyId the company ID
   * @return list of users with repeated blocked attempts
   */
  List<IntegrityIssueDTO> checkRepeatedBlockedAttempts(Long companyId);

  /**
   * Get the last integrity check result for a company.
   *
   * @param companyId the company ID
   * @return last check result, or null if no checks performed
   */
  IntegrityCheckResultDTO getLastCheckResult(Long companyId);

  /**
   * Get integrity check history for a company.
   *
   * @param companyId the company ID
   * @param limit maximum number of results
   * @return list of recent check results
   */
  List<IntegrityCheckResultDTO> getCheckHistory(Long companyId, int limit);
}
