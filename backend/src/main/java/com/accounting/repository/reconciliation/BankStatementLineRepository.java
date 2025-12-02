package com.accounting.repository.reconciliation;

import com.accounting.entity.reconciliation.BankStatementLine;
import com.accounting.entity.reconciliation.MatchStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repository for BankStatementLine entities.
 * Provides persistence for statement lines with matching queries.
 */
public interface BankStatementLineRepository extends JpaRepository<BankStatementLine, UUID> {

    /**
     * Find all statement lines for a reconciliation.
     *
     * @param reconciliationId reconciliation ID
     * @return list of statement lines ordered by line number
     */
    List<BankStatementLine> findByReconciliationIdOrderByLineNumber(UUID reconciliationId);

    /**
     * Find statement lines by reconciliation and match status.
     *
     * @param reconciliationId reconciliation ID
     * @param matchStatus      match status filter
     * @return list of statement lines
     */
    List<BankStatementLine> findByReconciliationIdAndMatchStatus(UUID reconciliationId, MatchStatus matchStatus);

    /**
     * Find statement line by ID within a reconciliation.
     *
     * @param reconciliationId reconciliation ID
     * @param lineId           statement line ID
     * @return statement line if exists
     */
    Optional<BankStatementLine> findByReconciliationIdAndId(UUID reconciliationId, UUID lineId);

    /**
     * Check if a voucher is already matched to a statement line in ANY reconciliation.
     * Critical for preventing double-matching.
     *
     * @param matchedVoucherId voucher ID
     * @return true if voucher is already matched
     */
    boolean existsByMatchedVoucherId(UUID matchedVoucherId);

    /**
     * Check if a voucher is already matched in a DIFFERENT reconciliation.
     * Used for validating matches across reconciliations.
     *
     * @param matchedVoucherId voucher ID
     * @param reconciliationId reconciliation to exclude
     * @return true if voucher is matched in another reconciliation
     */
    @Query("SELECT COUNT(l) > 0 FROM BankStatementLine l "
            + "WHERE l.matchedVoucherId = :voucherId "
            + "AND l.reconciliation.id != :reconciliationId")
    boolean existsByMatchedVoucherIdAndReconciliationIdNot(
            @Param("voucherId") UUID matchedVoucherId,
            @Param("reconciliationId") UUID reconciliationId);

    /**
     * Find potential matches for auto-matching based on date and amount.
     *
     * @param reconciliationId reconciliation ID
     * @param dateFrom         earliest date to consider
     * @param dateTo           latest date to consider
     * @param debitAmount      debit amount to match
     * @param creditAmount     credit amount to match
     * @return list of candidate statement lines
     */
    @Query("SELECT l FROM BankStatementLine l "
            + "WHERE l.reconciliation.id = :reconciliationId "
            + "AND l.matchStatus = 'UNMATCHED' "
            + "AND l.transactionDate BETWEEN :dateFrom AND :dateTo "
            + "AND (l.debitAmount = :debitAmount OR l.creditAmount = :creditAmount)")
    List<BankStatementLine> findPotentialMatches(
            @Param("reconciliationId") UUID reconciliationId,
            @Param("dateFrom") LocalDate dateFrom,
            @Param("dateTo") LocalDate dateTo,
            @Param("debitAmount") BigDecimal debitAmount,
            @Param("creditAmount") BigDecimal creditAmount);

    /**
     * Count statement lines by match status.
     *
     * @param reconciliationId reconciliation ID
     * @param matchStatus      match status
     * @return count
     */
    long countByReconciliationIdAndMatchStatus(UUID reconciliationId, MatchStatus matchStatus);

    /**
     * Calculate sum of debit amounts for matched lines.
     *
     * @param reconciliationId reconciliation ID
     * @return sum of debit amounts
     */
    @Query("SELECT COALESCE(SUM(l.debitAmount), 0) FROM BankStatementLine l "
            + "WHERE l.reconciliation.id = :reconciliationId AND l.matchStatus = 'MATCHED'")
    BigDecimal sumMatchedDebitAmount(@Param("reconciliationId") UUID reconciliationId);

    /**
     * Calculate sum of credit amounts for matched lines.
     *
     * @param reconciliationId reconciliation ID
     * @return sum of credit amounts
     */
    @Query("SELECT COALESCE(SUM(l.creditAmount), 0) FROM BankStatementLine l "
            + "WHERE l.reconciliation.id = :reconciliationId AND l.matchStatus = 'MATCHED'")
    BigDecimal sumMatchedCreditAmount(@Param("reconciliationId") UUID reconciliationId);

    /**
     * Calculate sum of all debit amounts for a reconciliation.
     *
     * @param reconciliationId reconciliation ID
     * @return sum of debit amounts
     */
    @Query("SELECT COALESCE(SUM(l.debitAmount), 0) FROM BankStatementLine l "
            + "WHERE l.reconciliation.id = :reconciliationId")
    BigDecimal sumTotalDebitAmount(@Param("reconciliationId") UUID reconciliationId);

    /**
     * Calculate sum of all credit amounts for a reconciliation.
     *
     * @param reconciliationId reconciliation ID
     * @return sum of credit amounts
     */
    @Query("SELECT COALESCE(SUM(l.creditAmount), 0) FROM BankStatementLine l "
            + "WHERE l.reconciliation.id = :reconciliationId")
    BigDecimal sumTotalCreditAmount(@Param("reconciliationId") UUID reconciliationId);

    /**
     * Unmatch all lines for a reconciliation (reset).
     *
     * @param reconciliationId reconciliation ID
     * @return number of updated rows
     */
    @Modifying
    @Query("UPDATE BankStatementLine l SET l.matchStatus = 'UNMATCHED', "
            + "l.matchedVoucherId = NULL, l.matchedAt = NULL, l.matchedById = NULL, "
            + "l.matchConfidence = NULL, l.matchReason = NULL, l.updatedAt = CURRENT_TIMESTAMP "
            + "WHERE l.reconciliation.id = :reconciliationId")
    int unmatchAllLines(@Param("reconciliationId") UUID reconciliationId);

    /**
     * Delete all statement lines for a reconciliation.
     *
     * @param reconciliationId reconciliation ID
     */
    void deleteByReconciliationId(UUID reconciliationId);

    /**
     * Count all statement lines for a reconciliation.
     *
     * @param reconciliationId reconciliation ID
     * @return count
     */
    long countByReconciliationId(UUID reconciliationId);

    /**
     * Paginated query for statement lines.
     *
     * @param reconciliationId reconciliation ID
     * @param pageable         pagination settings
     * @return page of statement lines
     */
    Page<BankStatementLine> findByReconciliationId(UUID reconciliationId, Pageable pageable);

    /**
     * Paginated query with match status filter.
     *
     * @param reconciliationId reconciliation ID
     * @param matchStatus      match status filter
     * @param pageable         pagination settings
     * @return page of statement lines
     */
    Page<BankStatementLine> findByReconciliationIdAndMatchStatus(UUID reconciliationId, MatchStatus matchStatus,
            Pageable pageable);
}
