package com.accounting.enums;

/**
 * Enum representing audit action types for Cash & Bank module compliance and auditing.
 * Used to maintain type-safe, documented audit actions for Story 6.6.
 *
 * <p>Each action type includes:
 * <ul>
 *   <li>A unique string value for database storage</li>
 *   <li>A human-readable display name</li>
 *   <li>A description of when this action is logged</li>
 *   <li>Metadata schema documentation for consistent audit log structure</li>
 * </ul>
 *
 * @see com.accounting.service.AuditService
 */
public enum CashBankAuditAction {

  // === Period Protection Actions (AC6.6-01) ===

  /**
   * Logged when a user attempts to modify or delete data in a closed accounting period.
   * <p>Metadata schema:
   * <pre>
   * {
   *   "transactionDate": "2025-01-15",
   *   "periodId": "uuid",
   *   "periodName": "January 2025",
   *   "periodStartDate": "2025-01-01",
   *   "periodEndDate": "2025-01-31",
   *   "attemptedOperation": "UPDATE|DELETE",
   *   "entityType": "CashReceipt|CashPayment|BankReconciliation",
   *   "entityId": "uuid",
   *   "reason": "Period is closed"
   * }
   * </pre>
   */
  PERIOD_BLOCK_ATTEMPT(
      "PERIOD_BLOCK_ATTEMPT",
      "Period Block Attempt",
      "User attempted to modify data in a closed period"),

  // === Integrity Check Actions (AC6.6-03) ===

  /**
   * Logged when a daily or period-close integrity check passes successfully.
   * <p>Metadata schema:
   * <pre>
   * {
   *   "checkType": "DAILY|PERIOD_CLOSE",
   *   "checkId": "uuid",
   *   "checksPerformed": ["DR_CR_PARITY", "DUPLICATE_REFS", "SEQUENCE_GAPS", "UNUSUAL_AMOUNTS"],
   *   "duration": 45000,
   *   "recordsChecked": 1500
   * }
   * </pre>
   */
  INTEGRITY_CHECK_PASS(
      "INTEGRITY_CHECK_PASS",
      "Integrity Check Passed",
      "Daily or period-close integrity check completed successfully"),

  /**
   * Logged when an integrity check fails and issues are detected.
   * <p>Metadata schema:
   * <pre>
   * {
   *   "checkType": "DAILY|PERIOD_CLOSE",
   *   "checkId": "uuid",
   *   "issueCount": 3,
   *   "issues": [
   *     {"type": "DR_CR_IMBALANCE", "severity": "HIGH", "details": "..."},
   *     {"type": "DUPLICATE_REF", "severity": "MEDIUM", "refNumber": "REC-001"}
   *   ],
   *   "alertsSent": true
   * }
   * </pre>
   */
  INTEGRITY_CHECK_FAIL(
      "INTEGRITY_CHECK_FAIL",
      "Integrity Check Failed",
      "Integrity check detected issues requiring attention"),

  // === Backup and Export Actions (AC6.6-02) ===

  /**
   * Logged when a scheduled or manual backup export is completed.
   * <p>Metadata schema:
   * <pre>
   * {
   *   "backupType": "WEEKLY|MANUAL",
   *   "periodFrom": "2025-01-01",
   *   "periodTo": "2025-01-31",
   *   "fileSize": 1048576,
   *   "fileCount": 5,
   *   "uploadLocation": "supabase://bucket/path",
   *   "manifestHash": "sha256:abc123...",
   *   "contents": ["accounts.json", "cashbook.json", "reconciliations.json", "audit.json"]
   * }
   * </pre>
   */
  BACKUP_EXPORT(
      "BACKUP_EXPORT",
      "Backup Export",
      "Scheduled or manual backup export completed"),

  // === Audit Explorer Actions (AC6.6-05) ===

  /**
   * Logged when a user queries the audit explorer.
   * <p>Metadata schema:
   * <pre>
   * {
   *   "filters": {
   *     "dateFrom": "2025-01-01",
   *     "dateTo": "2025-01-31",
   *     "actionTypes": ["RECEIPT_CREATED", "PAYMENT_POSTED"],
   *     "bankAccountId": 123,
   *     "userId": 456
   *   },
   *   "resultCount": 150,
   *   "page": 0,
   *   "size": 20
   * }
   * </pre>
   */
  AUDIT_EXPLORER_QUERY(
      "AUDIT_EXPLORER_QUERY",
      "Audit Explorer Query",
      "User queried audit logs through the explorer"),

  // === Anomaly Detection Actions (AC6.6-03, AC6.6-06) ===

  /**
   * Logged when an anomaly is detected in cash/bank transactions.
   * <p>Metadata schema:
   * <pre>
   * {
   *   "anomalyType": "UNUSUAL_AMOUNT|REPEATED_BLOCKS|SEQUENCE_GAP",
   *   "severity": "HIGH|MEDIUM|LOW",
   *   "details": "Transaction amount is 4.2 std dev from average",
   *   "entityType": "CashReceipt",
   *   "entityId": "uuid",
   *   "amount": 1000000.00,
   *   "threshold": 250000.00,
   *   "standardDeviations": 4.2
   * }
   * </pre>
   */
  ANOMALY_DETECTED(
      "ANOMALY_DETECTED",
      "Anomaly Detected",
      "Unusual pattern or transaction detected"),

  // === Purge Workflow Actions (AC6.6-05) ===

  /**
   * Logged when a Chief Accountant requests audit log purge.
   * <p>Metadata schema:
   * <pre>
   * {
   *   "requestId": "uuid",
   *   "dateFrom": "2020-01-01",
   *   "dateTo": "2020-12-31",
   *   "reason": "GDPR data retention compliance - customer request #12345",
   *   "estimatedRecords": 5420
   * }
   * </pre>
   */
  PURGE_REQUEST(
      "PURGE_REQUEST",
      "Purge Request",
      "Audit log purge requested"),

  /**
   * Logged when an Admin approves a purge request (must be different from requester).
   * <p>Metadata schema:
   * <pre>
   * {
   *   "requestId": "uuid",
   *   "requesterId": 123,
   *   "approverId": 456,
   *   "recordsPurged": 5420,
   *   "dateFrom": "2020-01-01",
   *   "dateTo": "2020-12-31"
   * }
   * </pre>
   */
  PURGE_APPROVE(
      "PURGE_APPROVE",
      "Purge Approved",
      "Audit log purge approved and executed"),

  /**
   * Logged when an Admin rejects a purge request.
   * <p>Metadata schema:
   * <pre>
   * {
   *   "requestId": "uuid",
   *   "requesterId": 123,
   *   "rejectedById": 456,
   *   "rejectionReason": "Insufficient justification for data deletion"
   * }
   * </pre>
   */
  PURGE_REJECT(
      "PURGE_REJECT",
      "Purge Rejected",
      "Audit log purge request rejected"),

  // === Alert Actions (AC6.6-06) ===

  /**
   * Logged when an alert notification is sent to designated users.
   * <p>Metadata schema:
   * <pre>
   * {
   *   "alertType": "BLOCKED_ATTEMPTS|INTEGRITY_FAILURE|UNUSUAL_AMOUNT|PURGE_REQUEST",
   *   "trigger": "3 blocked attempts in 1 hour by user 123",
   *   "recipients": ["manager@example.com", "admin@example.com"],
   *   "escalation": false,
   *   "channel": "EMAIL|IN_APP"
   * }
   * </pre>
   */
  ALERT_SENT(
      "ALERT_SENT",
      "Alert Sent",
      "Notification alert sent to designated users"),

  // === Export Control Actions (AC6.6-04) ===

  /**
   * Logged when a compliance export is generated (PDF/Excel with hash and watermark).
   * <p>Metadata schema:
   * <pre>
   * {
   *   "exportType": "CASH_BOOK|BANK_STATEMENT|RECONCILIATION|AUDIT_LOG",
   *   "format": "PDF|EXCEL",
   *   "periodStatus": "OPEN|CLOSED",
   *   "watermarked": true,
   *   "documentHash": "sha256:abc123...",
   *   "recordCount": 500,
   *   "periodId": "uuid",
   *   "bankAccountId": 123
   * }
   * </pre>
   */
  COMPLIANCE_EXPORT(
      "COMPLIANCE_EXPORT",
      "Compliance Export",
      "Export with compliance controls generated");

  private final String value;
  private final String displayName;
  private final String description;

  CashBankAuditAction(String value, String displayName, String description) {
    this.value = value;
    this.displayName = displayName;
    this.description = description;
  }

  /**
   * Get the string value for database storage.
   *
   * @return the action value string
   */
  public String getValue() {
    return value;
  }

  /**
   * Get the human-readable display name.
   *
   * @return the display name
   */
  public String getDisplayName() {
    return displayName;
  }

  /**
   * Get the description of when this action is logged.
   *
   * @return the action description
   */
  public String getDescription() {
    return description;
  }

  /**
   * Convert string value to CashBankAuditAction enum.
   *
   * @param value the action type string value
   * @return CashBankAuditAction enum or null if not found
   */
  public static CashBankAuditAction fromString(String value) {
    if (value == null) {
      return null;
    }
    for (CashBankAuditAction action : CashBankAuditAction.values()) {
      if (action.value.equals(value)) {
        return action;
      }
    }
    return null;
  }

  /**
   * Check if a string value is a valid cash/bank audit action.
   *
   * @param value the action type string value
   * @return true if valid, false otherwise
   */
  public static boolean isValid(String value) {
    return fromString(value) != null;
  }

  @Override
  public String toString() {
    return value;
  }
}
