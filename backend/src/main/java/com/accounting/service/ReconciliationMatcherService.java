package com.accounting.service;

import com.accounting.dto.reconciliation.AutoMatchConfigDTO;
import com.accounting.dto.reconciliation.AutoMatchResultDTO;
import com.accounting.dto.reconciliation.LedgerTransactionDTO;
import java.util.List;
import java.util.UUID;

/**
 * Service for matching bank statement lines to ledger transactions.
 * Implements auto-matching algorithm using date, amount, and reference similarity.
 *
 * <p>
 * AC6.5-03: Auto-Suggest Matches
 */
public interface ReconciliationMatcherService {

    /**
     * Run auto-match algorithm on unmatched statement lines.
     * Finds potential matches in ledger and optionally applies high-confidence matches.
     *
     * @param reconciliationId reconciliation ID
     * @param config           matching configuration (tolerances, thresholds)
     * @return auto-match result with suggestions and applied matches
     */
    AutoMatchResultDTO runAutoMatch(UUID reconciliationId, AutoMatchConfigDTO config);

    /**
     * Get ledger transactions available for matching.
     * Filters to posted vouchers within the reconciliation period for the bank account.
     *
     * @param reconciliationId reconciliation ID
     * @return list of unmatched ledger transactions
     */
    List<LedgerTransactionDTO> getLedgerTransactions(UUID reconciliationId);

    /**
     * Calculate match confidence score between a statement line and voucher.
     *
     * @param statementLineId statement line ID
     * @param voucherId       voucher ID
     * @return confidence score 0.0 to 1.0
     */
    double calculateMatchConfidence(UUID statementLineId, UUID voucherId);

    /**
     * Generate match reason explanation for UI display.
     *
     * @param statementLineId statement line ID
     * @param voucherId       voucher ID
     * @return human-readable match reason
     */
    String generateMatchReason(UUID statementLineId, UUID voucherId);

    /**
     * Validate that a voucher can be matched (not already matched elsewhere).
     *
     * @param voucherId        voucher ID
     * @param reconciliationId reconciliation ID (exclude from check)
     * @return true if voucher can be matched
     */
    boolean canMatchVoucher(UUID voucherId, UUID reconciliationId);
}
