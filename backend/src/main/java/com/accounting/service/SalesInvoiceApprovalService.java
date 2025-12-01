package com.accounting.service;

import com.accounting.dto.ApprovalWorkflowDTO;
import com.accounting.entity.SalesInvoice;
import java.util.List;
import java.util.UUID;

/**
 * Service interface for sales invoice approval workflows (AR module).
 * Implements maker-checker pattern with configurable threshold.
 * Mirrors ApprovalWorkflowService but for sales invoices instead of purchase
 * bills.
 */
public interface SalesInvoiceApprovalService {

    /**
     * Check if a sales invoice requires approval based on threshold and
     * sensitivity.
     *
     * @param invoice sales invoice to check
     * @return true if approval is required, false otherwise
     */
    boolean checkApprovalRequired(SalesInvoice invoice);

    /**
     * Submit a sales invoice for approval.
     * Creates approval workflow and sets invoice status to PENDING_APPROVAL.
     *
     * @param invoiceId   sales invoice ID
     * @param submitterId user ID of submitter
     * @return approval workflow DTO
     */
    ApprovalWorkflowDTO submitForApproval(UUID invoiceId, Long submitterId);

    /**
     * Approve a pending sales invoice.
     * Validates approver ≠ creator, posts voucher, and updates invoice status to
     * POSTED.
     *
     * @param workflowId approval workflow ID
     * @param approverId user ID of approver
     * @param reason     optional approval reason
     * @return updated approval workflow DTO
     */
    ApprovalWorkflowDTO approve(UUID workflowId, Long approverId, String reason);

    /**
     * Reject a pending sales invoice.
     * Reverts invoice to DRAFT status and requires rejection reason.
     *
     * @param workflowId approval workflow ID
     * @param approverId user ID of approver
     * @param reason     mandatory rejection reason
     * @return updated approval workflow DTO
     */
    ApprovalWorkflowDTO reject(UUID workflowId, Long approverId, String reason);

    /**
     * Auto-approve a sales invoice that doesn't require manual approval.
     * Creates shadow workflow record for audit trail.
     *
     * @param invoice     sales invoice to auto-approve
     * @param submitterId user ID of submitter
     * @return approval workflow DTO
     */
    ApprovalWorkflowDTO autoApprove(SalesInvoice invoice, Long submitterId);

    /**
     * Get all pending approval workflows for sales invoices.
     *
     * @return list of pending approval workflows
     */
    List<ApprovalWorkflowDTO> getPendingApprovals();

    /**
     * Get approval history for a specific sales invoice.
     *
     * @param invoiceId sales invoice ID
     * @return list of approval workflows for the invoice
     */
    List<ApprovalWorkflowDTO> getApprovalHistory(UUID invoiceId);

    /**
     * Get count of pending approvals for sales invoices.
     *
     * @return count of pending approvals
     */
    long getPendingApprovalsCount();
}
