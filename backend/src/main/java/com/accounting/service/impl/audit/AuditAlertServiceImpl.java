package com.accounting.service.impl.audit;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.accounting.dto.audit.IntegrityCheckResultDTO;
import com.accounting.dto.audit.IntegrityIssueDTO;
import com.accounting.entity.AuditLog;
import com.accounting.enums.CashBankAuditAction;
import com.accounting.repository.AuditLogRepository;
import com.accounting.security.CompanyContext;
import com.accounting.service.AuditAlertService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Implementation of AuditAlertService for sending audit-related alerts.
 *
 * <p>Currently implements logging-based alerts. In production, this would integrate with:
 * <ul>
 *   <li>Email service for email notifications</li>
 *   <li>Notification center for in-app notifications</li>
 *   <li>Slack/Teams webhooks for instant messaging</li>
 * </ul>
 */
@Service
@Transactional
public class AuditAlertServiceImpl implements AuditAlertService {

  private static final Logger logger = LoggerFactory.getLogger(AuditAlertServiceImpl.class);
  private static final int MANAGER_ESCALATION_THRESHOLD = 3;
  private static final int CHIEF_ACCOUNTANT_ESCALATION_THRESHOLD = 5;

  private final AuditLogRepository auditLogRepository;
  private final ObjectMapper objectMapper;

  public AuditAlertServiceImpl(
      AuditLogRepository auditLogRepository,
      ObjectMapper objectMapper) {
    this.auditLogRepository = auditLogRepository;
    this.objectMapper = objectMapper;
  }

  @Override
  public void sendBlockedAttemptAlert(Long userId, String userEmail, int attemptCount,
      int hoursPeriod) {
    logger.info("Sending blocked attempt alert for user {} ({} attempts in {} hours)",
        userId, attemptCount, hoursPeriod);

    String alertLevel;
    String recipients;

    if (attemptCount >= CHIEF_ACCOUNTANT_ESCALATION_THRESHOLD) {
      alertLevel = "ESCALATED";
      recipients = "CHIEF_ACCOUNTANT";
      logger.warn("ESCALATION: User {} has {} blocked attempts - alerting Chief Accountant",
          userId, attemptCount);
    } else if (attemptCount >= MANAGER_ESCALATION_THRESHOLD) {
      alertLevel = "WARNING";
      recipients = "MANAGER";
      logger.warn("WARNING: User {} has {} blocked attempts - alerting Manager",
          userId, attemptCount);
    } else {
      // Below threshold, no alert needed
      return;
    }

    // Log the alert
    logAlert("BLOCKED_ATTEMPTS", alertLevel, recipients,
        String.format("User %s has %d blocked attempts in %d hour(s)",
            userEmail, attemptCount, hoursPeriod),
        userId);

    // TODO: Integrate with email service
    // emailService.sendAlert(recipients, "Blocked Attempt Alert", message);

    // TODO: Integrate with notification center
    // notificationService.createNotification(recipients, "BLOCKED_ATTEMPTS", message);
  }

  @Override
  public void sendIntegrityCheckAlert(IntegrityCheckResultDTO checkResult) {
    if (checkResult.isPassed()) {
      return;
    }

    logger.warn("Sending integrity check failure alert - {} issues found",
        checkResult.getIssueCount());

    StringBuilder details = new StringBuilder();
    details.append("Integrity check failed with ").append(checkResult.getIssueCount())
        .append(" issue(s):\n");

    if (checkResult.getIssues() != null) {
      for (IntegrityIssueDTO issue : checkResult.getIssues()) {
        details.append("- [").append(issue.getSeverity()).append("] ")
            .append(issue.getType()).append(": ")
            .append(issue.getDescription()).append("\n");
      }
    }

    // Log the alert
    logAlert("INTEGRITY_CHECK_FAILURE", "CRITICAL", "ADMIN",
        details.toString(), null);

    // TODO: Integrate with email service for immediate notification
    // emailService.sendUrgentAlert("ADMIN", "Integrity Check Failure", details.toString());
  }

  @Override
  public void sendAnomalyAlerts(List<IntegrityIssueDTO> anomalies) {
    if (anomalies == null || anomalies.isEmpty()) {
      return;
    }

    logger.info("Sending alerts for {} anomalies", anomalies.size());

    for (IntegrityIssueDTO anomaly : anomalies) {
      String recipients = determineAnomalyRecipients(anomaly);
      String alertLevel = "HIGH".equals(anomaly.getSeverity()) ? "CRITICAL" : "WARNING";

      logAlert("ANOMALY_" + anomaly.getType(), alertLevel, recipients,
          anomaly.getDescription() + ": " + anomaly.getDetails(), null);

      logger.warn("Anomaly alert: [{}] {} - {}", anomaly.getSeverity(),
          anomaly.getType(), anomaly.getDescription());
    }
  }

  @Override
  public void sendPurgeRequestNotification(Long requesterId, String requesterEmail,
      LocalDate dateFrom, LocalDate dateTo, String reason) {
    logger.info("Sending purge request notification from user {} for dates {} to {}",
        requesterId, dateFrom, dateTo);

    String message = String.format(
        "Purge request from %s for audit logs from %s to %s. Reason: %s",
        requesterEmail, dateFrom, dateTo, reason);

    logAlert("PURGE_REQUEST", "INFO", "ADMIN", message, requesterId);

    // TODO: Integrate with email service
    // emailService.sendToRole("ADMIN", "Purge Request Pending Approval", message);
  }

  @Override
  public void sendPurgeApprovalNotification(Long requesterId, Long approverId, int recordsPurged) {
    logger.info("Sending purge approval notification - {} records purged by user {}",
        recordsPurged, approverId);

    String message = String.format(
        "Purge request approved. %d records were permanently deleted.",
        recordsPurged);

    logAlert("PURGE_APPROVED", "INFO", "CHIEF_ACCOUNTANT", message, requesterId);

    // TODO: Notify the original requester
  }

  @Override
  public void sendPurgeRejectionNotification(Long requesterId, Long rejectorId, String reason) {
    logger.info("Sending purge rejection notification to user {}", requesterId);

    String message = String.format("Purge request rejected. Reason: %s", reason);

    logAlert("PURGE_REJECTED", "INFO", "CHIEF_ACCOUNTANT", message, requesterId);

    // TODO: Notify the original requester
  }

  // === Private Methods ===

  private String determineAnomalyRecipients(IntegrityIssueDTO anomaly) {
    switch (anomaly.getType()) {
      case "UNUSUAL_AMOUNT":
        return "CHIEF_ACCOUNTANT";
      case "REPEATED_BLOCKED_ATTEMPTS":
        return "MANAGER,CHIEF_ACCOUNTANT";
      default:
        return "ADMIN";
    }
  }

  private void logAlert(String alertType, String alertLevel, String recipients,
      String message, Long targetUserId) {
    try {
      ObjectNode metadata = objectMapper.createObjectNode();
      metadata.put("alertType", alertType);
      metadata.put("alertLevel", alertLevel);
      metadata.put("recipients", recipients);
      metadata.put("message", message);
      if (targetUserId != null) {
        metadata.put("targetUserId", targetUserId);
      }

      AuditLog log = new AuditLog();
      log.setAction(CashBankAuditAction.ALERT_SENT.getValue());
      log.setEventType("CASH_BANK_AUDIT");
      log.setCompanyId(CompanyContext.getCompanyId());
      log.setMetadata(metadata);
      log.setSuccess(true);
      log.setCreatedAt(Instant.now());

      auditLogRepository.save(log);

      logger.debug("Logged ALERT_SENT: {} to {}", alertType, recipients);
    } catch (Exception e) {
      logger.error("Failed to log alert", e);
    }
  }
}
