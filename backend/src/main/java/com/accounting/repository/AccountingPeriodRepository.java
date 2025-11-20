package com.accounting.repository;

import com.accounting.entity.AccountingPeriod;
import com.accounting.entity.PeriodStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountingPeriodRepository
    extends JpaRepository<AccountingPeriod, UUID>, JpaSpecificationExecutor<AccountingPeriod> {

  /**
   * Find all periods for a company.
   *
   * @param companyId company ID
   * @return list of periods
   */
  List<AccountingPeriod> findByCompanyId(Long companyId);

  /**
   * Find period by company ID and period ID.
   *
   * @param companyId company ID
   * @param periodId period ID
   * @return optional period
   */
  Optional<AccountingPeriod> findByCompanyIdAndId(Long companyId, UUID periodId);

  /**
   * Find periods by company and status.
   *
   * @param companyId company ID
   * @param status period status
   * @return list of periods
   */
  List<AccountingPeriod> findByCompanyIdAndStatus(Long companyId, PeriodStatus status);

  /**
   * Find periods by company and fiscal year.
   *
   * @param companyId company ID
   * @param fiscalYear fiscal year
   * @return list of periods
   */
  List<AccountingPeriod> findByCompanyIdAndFiscalYear(Long companyId, Integer fiscalYear);

  /**
   * Find period by company, fiscal year, and period number.
   *
   * @param companyId company ID
   * @param fiscalYear fiscal year
   * @param periodNumber period number
   * @return optional period
   */
  Optional<AccountingPeriod> findByCompanyIdAndFiscalYearAndPeriodNumber(
      Long companyId, Integer fiscalYear, Integer periodNumber);

  /**
   * Find period that contains a specific date for a company.
   *
   * @param companyId company ID
   * @param date date to check
   * @return optional period
   */
  @Query("SELECT p FROM AccountingPeriod p WHERE p.companyId = :companyId AND :date BETWEEN p.startDate AND p.endDate")
  Optional<AccountingPeriod> findByCompanyIdAndDate(@Param("companyId") Long companyId, @Param("date") LocalDate date);

  /**
   * Find current period for a company (period containing today's date).
   *
   * @param companyId company ID
   * @return optional current period
   */
  @Query("SELECT p FROM AccountingPeriod p WHERE p.companyId = :companyId AND CURRENT_DATE BETWEEN p.startDate AND p.endDate")
  Optional<AccountingPeriod> findCurrentPeriodByCompanyId(@Param("companyId") Long companyId);

  /**
   * Find open periods for a company.
   *
   * @param companyId company ID
   * @return list of open periods
   */
  List<AccountingPeriod> findByCompanyIdAndStatusOrderByStartDate(Long companyId, PeriodStatus status);

  /**
   * Find open periods around the current period (current + prior periods + future periods).
   * Used for the period selector to show current + 3 prior/next open periods.
   *
   * @param companyId company ID
   * @param currentDate current date
   * @return list of open periods
   */
  @Query(value = "SELECT * FROM accounting_periods p WHERE p.company_id = :companyId AND p.status = CAST(:status AS VARCHAR) " +
         "ORDER BY p.start_date DESC", nativeQuery = true)
  List<AccountingPeriod> findOpenPeriodsAroundDate(
      @Param("companyId") Long companyId,
      @Param("status") PeriodStatus status,
      @Param("currentDate") LocalDate currentDate);

  /**
   * Count draft vouchers in a period.
   *
   * @param companyId company ID
   * @param periodId period ID
   * @return count of draft vouchers
   */
  @Query("SELECT COUNT(v) FROM Voucher v WHERE v.companyId = :companyId AND v.periodId = :periodId AND v.status = 'draft'")
  Long countDraftVouchersInPeriod(@Param("companyId") Long companyId, @Param("periodId") UUID periodId);

  /**
   * Find periods by company and date range.
   *
   * @param companyId company ID
   * @param startDate start date
   * @param endDate end date
   * @return list of periods
   */
  List<AccountingPeriod> findByCompanyIdAndStartDateBetweenOrderByStartDate(
      Long companyId, LocalDate startDate, LocalDate endDate);

  /**
   * Check if date range overlaps with existing periods for a company.
   *
   * @param companyId company ID
   * @param startDate start date
   * @param endDate end date
   * @return count of overlapping periods
   */
  @Query("SELECT COUNT(p) FROM AccountingPeriod p WHERE p.companyId = :companyId AND " +
         "((p.startDate <= :endDate AND p.endDate >= :startDate))")
  Long countOverlappingPeriods(
      @Param("companyId") Long companyId,
      @Param("startDate") LocalDate startDate,
      @Param("endDate") LocalDate endDate);
}