package com.accounting.repository;

import com.accounting.entity.Voucher;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VoucherRepository
    extends JpaRepository<Voucher, UUID>, JpaSpecificationExecutor<Voucher> {

  /**
   * Find all vouchers for a company.
   *
   * @param companyId company ID
   * @return list of vouchers
   */
  List<Voucher> findByCompanyId(Long companyId);

  /**
   * Find voucher by company ID and voucher ID.
   *
   * @param companyId company ID
   * @param voucherId voucher ID
   * @return optional voucher
   */
  Optional<Voucher> findByCompanyIdAndId(Long companyId, UUID voucherId);

  /**
   * Find vouchers by company and status.
   *
   * @param companyId company ID
   * @param status voucher status (draft, posted, unposted)
   * @return list of vouchers
   */
  List<Voucher> findByCompanyIdAndStatus(Long companyId, String status);

  /**
   * Find vouchers by company and date range.
   *
   * @param companyId company ID
   * @param dateFrom start date (inclusive)
   * @param dateTo end date (inclusive)
   * @return list of vouchers
   */
  List<Voucher> findByCompanyIdAndVoucherDateBetween(
      Long companyId, LocalDate dateFrom, LocalDate dateTo);

  /**
   * Count vouchers by company and status.
   *
   * @param companyId company ID
   * @param status voucher status
   * @return count of vouchers
   */
  long countByCompanyIdAndStatus(Long companyId, String status);

  /**
   * Check if voucher exists by company ID and voucher number.
   *
   * @param companyId company ID
   * @param voucherNumber voucher number
   * @param excludeId voucher ID to exclude (for updates)
   * @return true if voucher exists, false otherwise
   */
  @Query(
      "SELECT COUNT(v) > 0 FROM Voucher v WHERE v.companyId = :companyId AND v.voucherNumber = :voucherNumber AND (:excludeId IS NULL OR v.id != :excludeId)")
  boolean existsByCompanyIdAndVoucherNumber(
      @Param("companyId") Long companyId,
      @Param("voucherNumber") String voucherNumber,
      @Param("excludeId") UUID excludeId);

  /**
   * Check if voucher is referenced by other vouchers (has reversals or is referenced elsewhere).
   *
   * @param voucherId voucher ID to check
   * @return true if voucher is referenced, false otherwise
   */
  @Query("SELECT COUNT(v) > 0 FROM Voucher v WHERE v.reversalOf = :voucherId")
  boolean isReferenced(@Param("voucherId") UUID voucherId);

  /**
   * Find voucher IDs matching search term using native PostgreSQL unaccent function.
   * This method uses the unaccent_search() function for Vietnamese text matching.
   *
   * @param companyId company ID to filter by
   * @param searchTerm search term (will be matched against voucher_number and description)
   * @return list of voucher IDs matching the search
   */
  @Query(
      value =
          "SELECT id FROM vouchers WHERE company_id = :companyId "
              + "AND (unaccent_search(LOWER(voucher_number)) LIKE '%' || LOWER(unaccent_search(:searchTerm)) || '%' "
              + "OR unaccent_search(LOWER(description)) LIKE '%' || LOWER(unaccent_search(:searchTerm)) || '%')",
      nativeQuery = true)
  List<UUID> findIdsByCompanyIdAndSearchTerm(
      @Param("companyId") Long companyId, @Param("searchTerm") String searchTerm);
}
