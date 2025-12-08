package com.accounting.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.accounting.entity.VoucherLine;

public interface VoucherLineRepository
        extends JpaRepository<VoucherLine, UUID> {

    /**
     * Find all lines for a voucher.
     *
     * @param voucherId voucher ID
     * @return list of voucher lines ordered by line number
     */
    List<VoucherLine> findByVoucherIdOrderByLineNumberAsc(UUID voucherId);

    /**
     * Find all lines for a voucher by company ID (for company scoping).
     *
     * @param companyId company ID
     * @param voucherId voucher ID
     * @return list of voucher lines ordered by line number
     */
    List<VoucherLine> findByCompanyIdAndVoucherIdOrderByLineNumberAsc(
            Long companyId, UUID voucherId);

    /**
     * Delete all lines for a voucher.
     *
     * @param voucherId voucher ID
     */
    void deleteByVoucherId(UUID voucherId);

    /**
     * Count lines for a voucher.
     *
     * @param voucherId voucher ID
     * @return count of lines
     */
    long countByVoucherId(UUID voucherId);

    /**
     * Find lines by account ID (for account-level queries).
     *
     * @param companyId company ID
     * @param accountId account ID
     * @return list of voucher lines
     */
    List<VoucherLine> findByCompanyIdAndAccountId(Long companyId, Long accountId);

    /**
     * Find lines by account ID and bank account ID (for cash book with detailed
     * bank tracking).
     * This follows MISA pattern where transactions on TK 1121/1122 are tracked by
     * specific bank account.
     *
     * @param companyId     company ID
     * @param accountId     GL account ID (e.g., 1121)
     * @param bankAccountId bank account ID for detailed tracking
     * @return list of voucher lines for the specific bank account
     */
    List<VoucherLine> findByCompanyIdAndAccountIdAndBankAccountId(
            Long companyId, Long accountId, Long bankAccountId);

    /**
     * Aggregate posted voucher lines by account and period for trial balance.
     * Returns account ID, sum of debits, and sum of credits for the specified
     * period.
     *
     * @param companyId company ID
     * @param periodId  period ID
     * @return list of Object arrays: [accountId (Long), totalDebit (BigDecimal),
     *         totalCredit (BigDecimal)]
     */
    @Query("SELECT vl.accountId, SUM(vl.debit), SUM(vl.credit) " +
            "FROM VoucherLine vl " +
            "JOIN vl.voucher v " +
            "WHERE vl.companyId = :companyId " +
            "AND v.status = 'posted' " +
            "AND v.periodId = :periodId " +
            "GROUP BY vl.accountId")
    List<Object[]> aggregateByAccountAndPeriod(
            @Param("companyId") Long companyId,
            @Param("periodId") UUID periodId);

    /**
     * Calculate opening balances for all accounts up to (but not including) the
     * specified period start date.
     * Returns account ID, sum of debits, and sum of credits for all periods before
     * the given date.
     *
     * @param companyId       company ID
     * @param periodStartDate start date of the selected period (exclusive)
     * @return list of Object arrays: [accountId (Long), totalDebit (BigDecimal),
     *         totalCredit (BigDecimal)]
     */
    @Query("SELECT vl.accountId, SUM(vl.debit), SUM(vl.credit) " +
            "FROM VoucherLine vl " +
            "JOIN vl.voucher v " +
            "WHERE vl.companyId = :companyId " +
            "AND v.status = 'posted' " +
            "AND v.voucherDate < :periodStartDate " +
            "GROUP BY vl.accountId")
    List<Object[]> calculateOpeningBalances(
            @Param("companyId") Long companyId,
            @Param("periodStartDate") LocalDate periodStartDate);

    /**
     * Count transactions (voucher lines) per account for a period.
     * Used in drill-down to show transaction count per account.
     *
     * @param companyId company ID
     * @param startDate period start date
     * @param endDate period end date
     * @return list of Object arrays: [accountId (Long), transactionCount (Long)]
     */
    @Query("SELECT vl.accountId, COUNT(vl) " +
            "FROM VoucherLine vl " +
            "JOIN vl.voucher v " +
            "WHERE vl.companyId = :companyId " +
            "AND v.status = 'posted' " +
            "AND v.voucherDate >= :startDate " +
            "AND v.voucherDate <= :endDate " +
            "GROUP BY vl.accountId")
    List<Object[]> countTransactionsPerAccountForPeriod(
            @Param("companyId") Long companyId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    /**
     * Calculate period activity (debit/credit movement) for all accounts within a date range.
     * Used for Income Statement (B02) and Cash Flow Statement (B03) which need period-specific
     * activity rather than cumulative balances.
     *
     * Unlike calculateOpeningBalances which returns cumulative totals from inception,
     * this method returns only the activity within the specified date range.
     *
     * @param companyId company ID
     * @param startDate period start date (inclusive)
     * @param endDate period end date (inclusive)
     * @return list of Object arrays: [accountId (Long), totalDebit (BigDecimal), totalCredit (BigDecimal)]
     */
    @Query("SELECT vl.accountId, SUM(vl.debit), SUM(vl.credit) " +
            "FROM VoucherLine vl " +
            "JOIN vl.voucher v " +
            "WHERE vl.companyId = :companyId " +
            "AND v.status = 'posted' " +
            "AND v.voucherDate >= :startDate " +
            "AND v.voucherDate <= :endDate " +
            "GROUP BY vl.accountId")
    List<Object[]> calculatePeriodActivity(
            @Param("companyId") Long companyId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    // ==================== Trial Balance Drill-Down Queries ====================

    /**
     * Find vouchers with period debit amounts for a specific account.
     * Used for drill-down from Period Debit column in Trial Balance.
     *
     * @param companyId company ID
     * @param accountId account ID
     * @param periodId  period ID
     * @param pageable  pagination parameters
     * @return page of Object arrays: [voucherId (UUID), voucherNumber (String), voucherDate (LocalDate),
     *         description (String), debit (BigDecimal), credit (BigDecimal), status (String)]
     */
    @Query("SELECT v.id, v.voucherNumber, v.voucherDate, v.description, " +
            "SUM(vl.debit), SUM(vl.credit), v.status " +
            "FROM VoucherLine vl " +
            "JOIN vl.voucher v " +
            "WHERE vl.companyId = :companyId " +
            "AND vl.accountId = :accountId " +
            "AND v.status = 'posted' " +
            "AND v.periodId = :periodId " +
            "AND vl.debit > 0 " +
            "GROUP BY v.id, v.voucherNumber, v.voucherDate, v.description, v.status")
    Page<Object[]> findPeriodDebitVouchers(
            @Param("companyId") Long companyId,
            @Param("accountId") Long accountId,
            @Param("periodId") UUID periodId,
            Pageable pageable);

    /**
     * Find vouchers with period credit amounts for a specific account.
     * Used for drill-down from Period Credit column in Trial Balance.
     */
    @Query("SELECT v.id, v.voucherNumber, v.voucherDate, v.description, " +
            "SUM(vl.debit), SUM(vl.credit), v.status " +
            "FROM VoucherLine vl " +
            "JOIN vl.voucher v " +
            "WHERE vl.companyId = :companyId " +
            "AND vl.accountId = :accountId " +
            "AND v.status = 'posted' " +
            "AND v.periodId = :periodId " +
            "AND vl.credit > 0 " +
            "GROUP BY v.id, v.voucherNumber, v.voucherDate, v.description, v.status")
    Page<Object[]> findPeriodCreditVouchers(
            @Param("companyId") Long companyId,
            @Param("accountId") Long accountId,
            @Param("periodId") UUID periodId,
            Pageable pageable);

    /**
     * Find vouchers with opening balance debit amounts for a specific account.
     * Returns vouchers from all periods before the specified start date with debit entries.
     */
    @Query("SELECT v.id, v.voucherNumber, v.voucherDate, v.description, " +
            "SUM(vl.debit), SUM(vl.credit), v.status " +
            "FROM VoucherLine vl " +
            "JOIN vl.voucher v " +
            "WHERE vl.companyId = :companyId " +
            "AND vl.accountId = :accountId " +
            "AND v.status = 'posted' " +
            "AND v.voucherDate < :periodStartDate " +
            "AND vl.debit > 0 " +
            "GROUP BY v.id, v.voucherNumber, v.voucherDate, v.description, v.status")
    Page<Object[]> findOpeningDebitVouchers(
            @Param("companyId") Long companyId,
            @Param("accountId") Long accountId,
            @Param("periodStartDate") LocalDate periodStartDate,
            Pageable pageable);

    /**
     * Find vouchers with opening balance credit amounts for a specific account.
     * Returns vouchers from all periods before the specified start date with credit entries.
     */
    @Query("SELECT v.id, v.voucherNumber, v.voucherDate, v.description, " +
            "SUM(vl.debit), SUM(vl.credit), v.status " +
            "FROM VoucherLine vl " +
            "JOIN vl.voucher v " +
            "WHERE vl.companyId = :companyId " +
            "AND vl.accountId = :accountId " +
            "AND v.status = 'posted' " +
            "AND v.voucherDate < :periodStartDate " +
            "AND vl.credit > 0 " +
            "GROUP BY v.id, v.voucherNumber, v.voucherDate, v.description, v.status")
    Page<Object[]> findOpeningCreditVouchers(
            @Param("companyId") Long companyId,
            @Param("accountId") Long accountId,
            @Param("periodStartDate") LocalDate periodStartDate,
            Pageable pageable);

    /**
     * Find vouchers contributing to closing debit balance for a specific account.
     * Returns all vouchers up to and including the period end date with net debit effect.
     * For closing balance drill-down, we return all vouchers that have debit entries.
     */
    @Query("SELECT v.id, v.voucherNumber, v.voucherDate, v.description, " +
            "SUM(vl.debit), SUM(vl.credit), v.status " +
            "FROM VoucherLine vl " +
            "JOIN vl.voucher v " +
            "WHERE vl.companyId = :companyId " +
            "AND vl.accountId = :accountId " +
            "AND v.status = 'posted' " +
            "AND v.voucherDate <= :periodEndDate " +
            "AND vl.debit > 0 " +
            "GROUP BY v.id, v.voucherNumber, v.voucherDate, v.description, v.status")
    Page<Object[]> findClosingDebitVouchers(
            @Param("companyId") Long companyId,
            @Param("accountId") Long accountId,
            @Param("periodEndDate") LocalDate periodEndDate,
            Pageable pageable);

    /**
     * Find vouchers contributing to closing credit balance for a specific account.
     * Returns all vouchers up to and including the period end date with net credit effect.
     */
    @Query("SELECT v.id, v.voucherNumber, v.voucherDate, v.description, " +
            "SUM(vl.debit), SUM(vl.credit), v.status " +
            "FROM VoucherLine vl " +
            "JOIN vl.voucher v " +
            "WHERE vl.companyId = :companyId " +
            "AND vl.accountId = :accountId " +
            "AND v.status = 'posted' " +
            "AND v.voucherDate <= :periodEndDate " +
            "AND vl.credit > 0 " +
            "GROUP BY v.id, v.voucherNumber, v.voucherDate, v.description, v.status")
    Page<Object[]> findClosingCreditVouchers(
            @Param("companyId") Long companyId,
            @Param("accountId") Long accountId,
            @Param("periodEndDate") LocalDate periodEndDate,
            Pageable pageable);

    /**
     * Calculate total amount for period debit drill-down (for summary).
     */
    @Query("SELECT COALESCE(SUM(vl.debit), 0) " +
            "FROM VoucherLine vl " +
            "JOIN vl.voucher v " +
            "WHERE vl.companyId = :companyId " +
            "AND vl.accountId = :accountId " +
            "AND v.status = 'posted' " +
            "AND v.periodId = :periodId " +
            "AND vl.debit > 0")
    BigDecimal sumPeriodDebit(
            @Param("companyId") Long companyId,
            @Param("accountId") Long accountId,
            @Param("periodId") UUID periodId);

    /**
     * Calculate total amount for period credit drill-down (for summary).
     */
    @Query("SELECT COALESCE(SUM(vl.credit), 0) " +
            "FROM VoucherLine vl " +
            "JOIN vl.voucher v " +
            "WHERE vl.companyId = :companyId " +
            "AND vl.accountId = :accountId " +
            "AND v.status = 'posted' " +
            "AND v.periodId = :periodId " +
            "AND vl.credit > 0")
    BigDecimal sumPeriodCredit(
            @Param("companyId") Long companyId,
            @Param("accountId") Long accountId,
            @Param("periodId") UUID periodId);

    /**
     * Calculate total debit for opening balance drill-down.
     */
    @Query("SELECT COALESCE(SUM(vl.debit), 0) " +
            "FROM VoucherLine vl " +
            "JOIN vl.voucher v " +
            "WHERE vl.companyId = :companyId " +
            "AND vl.accountId = :accountId " +
            "AND v.status = 'posted' " +
            "AND v.voucherDate < :periodStartDate " +
            "AND vl.debit > 0")
    BigDecimal sumOpeningDebit(
            @Param("companyId") Long companyId,
            @Param("accountId") Long accountId,
            @Param("periodStartDate") LocalDate periodStartDate);

    /**
     * Calculate total credit for opening balance drill-down.
     */
    @Query("SELECT COALESCE(SUM(vl.credit), 0) " +
            "FROM VoucherLine vl " +
            "JOIN vl.voucher v " +
            "WHERE vl.companyId = :companyId " +
            "AND vl.accountId = :accountId " +
            "AND v.status = 'posted' " +
            "AND v.voucherDate < :periodStartDate " +
            "AND vl.credit > 0")
    BigDecimal sumOpeningCredit(
            @Param("companyId") Long companyId,
            @Param("accountId") Long accountId,
            @Param("periodStartDate") LocalDate periodStartDate);

    /**
     * Calculate total debit for closing balance drill-down.
     */
    @Query("SELECT COALESCE(SUM(vl.debit), 0) " +
            "FROM VoucherLine vl " +
            "JOIN vl.voucher v " +
            "WHERE vl.companyId = :companyId " +
            "AND vl.accountId = :accountId " +
            "AND v.status = 'posted' " +
            "AND v.voucherDate <= :periodEndDate " +
            "AND vl.debit > 0")
    BigDecimal sumClosingDebit(
            @Param("companyId") Long companyId,
            @Param("accountId") Long accountId,
            @Param("periodEndDate") LocalDate periodEndDate);

    /**
     * Calculate total credit for closing balance drill-down.
     */
    @Query("SELECT COALESCE(SUM(vl.credit), 0) " +
            "FROM VoucherLine vl " +
            "JOIN vl.voucher v " +
            "WHERE vl.companyId = :companyId " +
            "AND vl.accountId = :accountId " +
            "AND v.status = 'posted' " +
            "AND v.voucherDate <= :periodEndDate " +
            "AND vl.credit > 0")
    BigDecimal sumClosingCredit(
            @Param("companyId") Long companyId,
            @Param("accountId") Long accountId,
            @Param("periodEndDate") LocalDate periodEndDate);
}
