package com.accounting.dto.audit;

import java.math.BigDecimal;

/**
 * DTO representing a single integrity check issue.
 */
public class IntegrityIssueDTO {

  private String type;
  private String severity; // HIGH, MEDIUM, LOW
  private String description;
  private String entityType;
  private String entityId;
  private String details;
  private BigDecimal amount;
  private BigDecimal expectedAmount;
  private String referenceNumber;

  public IntegrityIssueDTO() {
  }

  /**
   * Create a DR/CR imbalance issue.
   */
  public static IntegrityIssueDTO drCrImbalance(String entityType, String entityId,
      BigDecimal debitTotal, BigDecimal creditTotal) {
    IntegrityIssueDTO issue = new IntegrityIssueDTO();
    issue.type = "DR_CR_IMBALANCE";
    issue.severity = "HIGH";
    issue.description = "Debit and credit totals do not balance";
    issue.entityType = entityType;
    issue.entityId = entityId;
    issue.amount = debitTotal.subtract(creditTotal).abs();
    issue.details = String.format("Debit: %s, Credit: %s, Difference: %s",
        debitTotal, creditTotal, issue.amount);
    return issue;
  }

  /**
   * Create a duplicate reference issue.
   */
  public static IntegrityIssueDTO duplicateReference(String entityType, String entityId,
      String referenceNumber, int count) {
    IntegrityIssueDTO issue = new IntegrityIssueDTO();
    issue.type = "DUPLICATE_REF";
    issue.severity = "MEDIUM";
    issue.description = "Duplicate transaction reference found";
    issue.entityType = entityType;
    issue.entityId = entityId;
    issue.referenceNumber = referenceNumber;
    issue.details = String.format("Reference '%s' appears %d times", referenceNumber, count);
    return issue;
  }

  /**
   * Create a sequence gap issue.
   */
  public static IntegrityIssueDTO sequenceGap(String entityType, String from, String to) {
    IntegrityIssueDTO issue = new IntegrityIssueDTO();
    issue.type = "SEQUENCE_GAP";
    issue.severity = "MEDIUM";
    issue.description = "Gap detected in number sequence";
    issue.entityType = entityType;
    issue.details = String.format("Gap between '%s' and '%s'", from, to);
    return issue;
  }

  /**
   * Create an unusual amount issue.
   */
  public static IntegrityIssueDTO unusualAmount(String entityType, String entityId,
      BigDecimal amount, BigDecimal average, double standardDeviations) {
    IntegrityIssueDTO issue = new IntegrityIssueDTO();
    issue.type = "UNUSUAL_AMOUNT";
    issue.severity = standardDeviations > 5 ? "HIGH" : "MEDIUM";
    issue.description = "Transaction amount is unusually large";
    issue.entityType = entityType;
    issue.entityId = entityId;
    issue.amount = amount;
    issue.expectedAmount = average;
    issue.details = String.format("Amount %s is %.1f standard deviations from average %s",
        amount, standardDeviations, average);
    return issue;
  }

  /**
   * Create a repeated blocked attempts issue.
   */
  public static IntegrityIssueDTO repeatedBlockedAttempts(Long userId, String userEmail,
      int attemptCount, int hours) {
    IntegrityIssueDTO issue = new IntegrityIssueDTO();
    issue.type = "REPEATED_BLOCKS";
    issue.severity = attemptCount >= 5 ? "HIGH" : "MEDIUM";
    issue.description = "User has repeated blocked attempts";
    issue.entityType = "User";
    issue.entityId = userId != null ? userId.toString() : "";
    issue.details = String.format("User '%s' has %d blocked attempts in %d hour(s)",
        userEmail, attemptCount, hours);
    return issue;
  }

  // Getters and Setters

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public String getSeverity() {
    return severity;
  }

  public void setSeverity(String severity) {
    this.severity = severity;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getEntityType() {
    return entityType;
  }

  public void setEntityType(String entityType) {
    this.entityType = entityType;
  }

  public String getEntityId() {
    return entityId;
  }

  public void setEntityId(String entityId) {
    this.entityId = entityId;
  }

  public String getDetails() {
    return details;
  }

  public void setDetails(String details) {
    this.details = details;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public void setAmount(BigDecimal amount) {
    this.amount = amount;
  }

  public BigDecimal getExpectedAmount() {
    return expectedAmount;
  }

  public void setExpectedAmount(BigDecimal expectedAmount) {
    this.expectedAmount = expectedAmount;
  }

  public String getReferenceNumber() {
    return referenceNumber;
  }

  public void setReferenceNumber(String referenceNumber) {
    this.referenceNumber = referenceNumber;
  }
}
