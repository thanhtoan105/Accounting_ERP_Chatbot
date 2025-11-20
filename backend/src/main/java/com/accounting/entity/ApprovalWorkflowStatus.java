package com.accounting.entity;

/**
 * Status of an approval workflow for purchase bills.
 * Tracks the approval lifecycle from submission through approval/rejection.
 */
public enum ApprovalWorkflowStatus {
  /**
   * Workflow is pending approval from an authorized approver.
   */
  PENDING("Pending"),

  /**
   * Workflow has been approved and bill has been posted.
   */
  APPROVED("Approved"),

  /**
   * Workflow has been rejected with a mandatory reason.
   */
  REJECTED("Rejected"),

  /**
   * Bill was auto-approved (below threshold and not sensitive).
   * Shadow record for audit trail purposes.
   */
  AUTO_APPROVED("Auto-Approved");

  private final String displayName;

  ApprovalWorkflowStatus(String displayName) {
    this.displayName = displayName;
  }

  public String getDisplayName() {
    return displayName;
  }
}
