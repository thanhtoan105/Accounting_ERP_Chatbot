package com.accounting.entity.reconciliation;

/**
 * Status of a bank reconciliation session.
 */
public enum ReconciliationStatus {
    /** Reconciliation created but no statement imported yet */
    NOT_STARTED,

    /** Statement imported and matching in progress */
    IN_PROGRESS,

    /** All lines matched/adjusted and reconciliation approved */
    COMPLETED
}
