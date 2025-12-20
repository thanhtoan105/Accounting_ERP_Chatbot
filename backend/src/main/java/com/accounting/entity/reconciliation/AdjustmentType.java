package com.accounting.entity.reconciliation;

/**
 * Type of reconciliation adjustment.
 */
public enum AdjustmentType {
    /** Bank service charges and fees */
    BANK_FEE,

    /** Interest earned on bank account */
    INTEREST_INCOME,

    /** Interest charged on bank account */
    INTEREST_EXPENSE,

    /** Other adjustments not categorized above */
    OTHER
}
