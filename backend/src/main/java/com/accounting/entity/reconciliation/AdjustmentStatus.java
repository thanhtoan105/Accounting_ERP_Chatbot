package com.accounting.entity.reconciliation;

/**
 * Status of a reconciliation adjustment in the approval workflow.
 */
public enum AdjustmentStatus {
    /** Adjustment created, awaiting approval */
    PENDING,

    /** Adjustment approved by Chief Accountant */
    APPROVED,

    /** Voucher created and posted for this adjustment */
    POSTED,

    /** Adjustment rejected by Chief Accountant */
    REJECTED
}
