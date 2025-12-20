package com.accounting.enums;

/**
 * Enum representing different types of amounts in Trial Balance drill-down.
 * Used to identify which column the user clicked on to initiate drill-down.
 */
public enum AmountType {
    /** Opening balance - debit side (balances before period start) */
    OPENING_DEBIT,

    /** Opening balance - credit side (balances before period start) */
    OPENING_CREDIT,

    /** Period activity - debit side (debits during the period) */
    PERIOD_DEBIT,

    /** Period activity - credit side (credits during the period) */
    PERIOD_CREDIT,

    /** Closing balance - debit side (calculated: opening + period) */
    CLOSING_DEBIT,

    /** Closing balance - credit side (calculated: opening + period) */
    CLOSING_CREDIT
}
