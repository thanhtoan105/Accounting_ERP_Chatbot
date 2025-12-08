package com.accounting.service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.accounting.dto.VoucherDTO;
import com.accounting.dto.cashbook.CashBookFilterDTO;
import com.accounting.dto.cashbook.CashBookResponseDTO;
import com.accounting.dto.cashbook.CashBookSummaryDTO;

/**
 * Service for Cash Book / Bank Book viewing and querying.
 * Provides transaction ledger with running balances, multi-account summary, and
 * voucher drill-down.
 * 
 * <p>
 * AC6.4-01: Per-account view with filters and running balance
 * <p>
 * AC6.4-03: Multi-account aggregated view
 * <p>
 * AC6.4-07: Performance target ≤2s for typical month (≤5k TX)
 */
public interface CashBookService {

    /**
     * Get cash book transactions for a specific bank/cash account.
     * Returns transaction ledger with running balances (AC6.4-01).
     *
     * @param bankAccountId   bank account ID
     * @param dateFrom        start date filter (inclusive, optional)
     * @param dateTo          end date filter (inclusive, optional)
     * @param transactionType filter by type: "receipt", "payment", or "all"
     *                        (optional)
     * @param reference       reference search filter (optional)
     * @param page            page number (0-based)
     * @param size            page size
     * @return cash book response with transactions and running balances
     */
    CashBookResponseDTO getCashBook(
            Long bankAccountId,
            LocalDate dateFrom,
            LocalDate dateTo,
            String transactionType,
            String reference,
            int page,
            int size);

    /**
     * Get cash book transactions using filter DTO.
     * 
     * @param filter filter parameters
     * @return cash book response with transactions and running balances
     */
    CashBookResponseDTO getCashBook(CashBookFilterDTO filter);

    /**
     * Get multi-account cash book summary (AC6.4-03).
     * Returns aggregated totals for multiple bank/cash accounts.
     *
     * @param accountIds list of bank account IDs (optional, null for all active
     *                   accounts)
     * @param dateFrom   start date filter (inclusive, optional)
     * @param dateTo     end date filter (inclusive, optional)
     * @return summary with account totals and grand totals
     */
    CashBookSummaryDTO getCashBookSummary(
            List<Long> accountIds,
            LocalDate dateFrom,
            LocalDate dateTo);

    /**
     * Get voucher detail for drill-down (AC6.4-02).
     * Returns full voucher with lines and attachments.
     *
     * @param bankAccountId bank account ID (for context validation)
     * @param voucherId     voucher ID
     * @return voucher DTO with lines and attachments
     */
    VoucherDTO getVoucherDetail(Long bankAccountId, UUID voucherId);

    /**
     * Count total transactions for a bank account within date range.
     * Used for pagination metadata and async export decision.
     *
     * @param bankAccountId   bank account ID
     * @param dateFrom        start date filter (inclusive, optional)
     * @param dateTo          end date filter (inclusive, optional)
     * @param transactionType filter by type: "receipt", "payment", or "all"
     *                        (optional)
     * @return total transaction count
     */
    long countTransactions(
            Long bankAccountId,
            LocalDate dateFrom,
            LocalDate dateTo,
            String transactionType);
}
