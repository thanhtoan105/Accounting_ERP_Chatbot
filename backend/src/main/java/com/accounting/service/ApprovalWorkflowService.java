package com.accounting.service;

import com.accounting.dto.ApprovalWorkflowDTO;
import com.accounting.entity.ApprovalWorkflow;
import com.accounting.entity.PurchaseBill;
import java.util.List;
import java.util.UUID;

/**
 * Service for managing purchase bill approval workflows.
 * Implements maker-checker pattern with configurable threshold.
 */
public interface ApprovalWorkflowService {

  /**
   * Check if a purchase bill requires approval workflow.
   * Returns true if bill amount > threshold OR bill is marked sensitive.
   *
   * @param bill the purchase bill to check
   * @return true if approval is required
   */
  boolean checkApprovalRequired(PurchaseBill bill);

  /**
   * Submit a purchase bill for approval.
   * Creates approval workflow, updates bill status to PENDING_APPROVAL,
   * and triggers notification to approvers.
   *
   * @param billId the purchase bill ID
   * @param submitterId the user ID submitting for approval
   * @return the created approval workflow DTO
   * @throws IllegalStateException if bill is not in DRAFT status
   * @throws IllegalArgumentException if bill doesn't require approval
   */
  ApprovalWorkflowDTO submitForApproval(UUID billId, Long submitterId);

  /**
   * Approve a purchase bill.
   * Validates approver ≠ creator, updates workflow status to APPROVED,
   * posts bill via voucher engine, and sends notification to creator.
   *
   * @param workflowId the approval workflow ID
   * @param approverId the user ID approving
   * @param reason optional approval reason
   * @return the updated approval workflow DTO
   * @throws IllegalStateException if workflow is not in PENDING status
   * @throws IllegalArgumentException if approver = creator
   * @throws IllegalStateException if period is closed
   */
  ApprovalWorkflowDTO approve(UUID workflowId, Long approverId, String reason);

  /**
   * Reject a purchase bill.
   * Updates workflow status to REJECTED, bill status to REJECTED,
   * and sends notification to creator with mandatory rejection reason.
   *
   * @param workflowId the approval workflow ID
   * @param approverId the user ID rejecting
   * @param reason mandatory rejection reason
   * @return the updated approval workflow DTO
   * @throws IllegalStateException if workflow is not in PENDING status
   * @throws IllegalArgumentException if approver = creator
   * @throws IllegalArgumentException if reason is blank
   */
  ApprovalWorkflowDTO reject(UUID workflowId, Long approverId, String reason);

  /**
   * Auto-approve a purchase bill that doesn't meet threshold.
   * Creates shadow workflow record for audit trail with AUTO_APPROVED status.
   *
   * @param bill the purchase bill to auto-approve
   * @param submitterId the user ID submitting
   * @return the created auto-approval workflow DTO
   */
  ApprovalWorkflowDTO autoApprove(PurchaseBill bill, Long submitterId);

  /**
   * Get pending approval workflows for the current company.
   * Used to display approval queue for Chief Accountant/CFO.
   *
   * @return list of pending approval workflow DTOs
   */
  List<ApprovalWorkflowDTO> getPendingApprovals();

  /**
   * Get approval workflow history for a purchase bill.
   * Returns all workflows for the bill (supports resubmission scenarios).
   *
   * @param billId the purchase bill ID
   * @return list of approval workflow DTOs in chronological order
   */
  List<ApprovalWorkflowDTO> getApprovalHistory(UUID billId);

  /**
   * Get count of pending approvals for notification badge.
   *
   * @return count of pending workflows
   */
  long getPendingApprovalsCount();
}
