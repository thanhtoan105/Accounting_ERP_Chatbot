package com.accounting.service;

import java.util.List;

import com.accounting.dto.audit.IntegrityCheckResultDTO;
import com.accounting.dto.audit.IntegrityIssueDTO;

/**
 * Service interface for sending audit-related alerts and notifications.
 *
 * <p>Alert types:
 * <ul>
 *   <li>Blocked attempt alerts (escalation after threshold)</li>
 *   <li>Integrity check failure alerts</li>
 *   <li>Anomaly detection alerts</li>
 *   <li>Purge request notifications</li>
 * </ul>
 *
 * @see com.accounting.enums.CashBankAuditAction#ALERT_SENT
 */
public interface AuditAlertService {

  /**
   * Send alert for repeated blocked attempts.
   *
   * <p>Alert thresholds:
   * <ul>
   *   <li>≥3 attempts in 1 hour: Alert to user's manager</li>
   *   <li>≥5 attempts in 1 hour: Escalate to Chief Accountant</li>
   * </ul>
   *
   * @param userId the user with blocked attempts
   * @param userEmail the user's email
   * @param attemptCount number of blocked attempts
   * @param hoursPeriod the time period in hours
   */
  void sendBlockedAttemptAlert(Long userId, String userEmail, int attemptCount, int hoursPeriod);

  /**
   * Send alert for integrity check failure.
   *
   * <p>Recipients: Admin users
   * <p>Escalation: Immediate
   *
   * @param checkResult the integrity check result with issues
   */
  void sendIntegrityCheckAlert(IntegrityCheckResultDTO checkResult);

  /**
   * Send alert for detected anomalies.
   *
   * <p>Alert types:
   * <ul>
   *   <li>Unusual amounts (>3 std dev): Chief Accountant</li>
   *   <li>Repeated blocked attempts: Manager → Chief Accountant</li>
   * </ul>
   *
   * @param anomalies list of detected anomalies
   */
  void sendAnomalyAlerts(List<IntegrityIssueDTO> anomalies);

  /**
   * Send notification for purge request.
   *
   * <p>Recipients: Admin users
   *
   * @param requesterId the user who requested the purge
   * @param requesterEmail the requester's email
   * @param dateFrom purge start date
   * @param dateTo purge end date
   * @param reason the reason for purge
   */
  void sendPurgeRequestNotification(Long requesterId, String requesterEmail,
      java.time.LocalDate dateFrom, java.time.LocalDate dateTo, String reason);

  /**
   * Send notification for purge approval.
   *
   * @param requesterId the user who requested the purge
   * @param approverId the user who approved the purge
   * @param recordsPurged number of records purged
   */
  void sendPurgeApprovalNotification(Long requesterId, Long approverId, int recordsPurged);

  /**
   * Send notification for purge rejection.
   *
   * @param requesterId the user who requested the purge
   * @param rejectorId the user who rejected the purge
   * @param reason the rejection reason
   */
  void sendPurgeRejectionNotification(Long requesterId, Long rejectorId, String reason);

  /**
   * Notify about hash chain mismatch detected during daily verification.
   *
   * <p>Recipients: ADMIN, CHIEF_ACCOUNTANT
   * <p>Escalation: Immediate (CRITICAL level)
   *
   * @param result the verification result containing mismatch details
   */
  void notifyHashMismatch(com.accounting.service.audit.dto.AuditChainVerificationResult result);

  /**
   * Notify about verification error during daily chain integrity check.
   *
   * <p>Recipients: ADMIN
   * <p>Escalation: High priority
   *
   * @param companyId the company being verified
   * @param date the date being verified
   * @param e the exception that occurred
   */
  void notifyVerificationError(Long companyId, java.time.LocalDate date, Exception e);
}
