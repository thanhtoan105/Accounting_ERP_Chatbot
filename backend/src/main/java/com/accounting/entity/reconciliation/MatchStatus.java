package com.accounting.entity.reconciliation;

/**
 * Match status of a bank statement line.
 */
public enum MatchStatus {
    /** Line not yet matched to any voucher */
    UNMATCHED,

    /** Line matched to a voucher */
    MATCHED,

    /** Line requires an adjustment voucher (bank fee, interest, etc.) */
    ADJUSTMENT_REQUIRED
}
