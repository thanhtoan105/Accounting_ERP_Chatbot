package com.accounting.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.accounting.entity.ARPayment;
import com.accounting.entity.ReceiptStatus;

public interface ARPaymentRepository
    extends JpaRepository<ARPayment, UUID>, JpaSpecificationExecutor<ARPayment> {

  /**
   * Find all receipts for a company.
   *
   * @param companyId company ID
   * @return list of receipts
   */
  List<ARPayment> findByCompanyId(Long companyId);

  /**
   * Find receipt by company ID and receipt ID.
   *
   * @param companyId company ID
   * @param receiptId receipt ID
   * @return optional receipt
   */
  Optional<ARPayment> findByCompanyIdAndId(Long companyId, UUID receiptId);

  /**
   * Find receipts by company and status.
   *
   * @param companyId company ID
   * @param status receipt status
   * @return list of receipts
   */
  List<ARPayment> findByCompanyIdAndStatus(Long companyId, ReceiptStatus status);

  /**
   * Find receipts by company and date range.
   *
   * @param companyId company ID
   * @param dateFrom start date (inclusive)
   * @param dateTo end date (inclusive)
   * @return list of receipts
   */
  List<ARPayment> findByCompanyIdAndReceiptDateBetween(
      Long companyId, LocalDate dateFrom, LocalDate dateTo);

  /**
   * Find receipts by company and customer.
   *
   * @param companyId company ID
   * @param customerId customer ID
   * @return list of receipts
   */
  List<ARPayment> findByCompanyIdAndCustomerId(Long companyId, Long customerId);

  /**
   * Find standalone receipts (advances/on-account) by company.
   *
   * @param companyId company ID
   * @param isStandalone standalone flag
   * @return list of standalone receipts
   */
  List<ARPayment> findByCompanyIdAndIsStandalone(Long companyId, Boolean isStandalone);

  /**
   * Check if receipt exists with duplicate receipt number+year combination.
   * Unique constraint: UNIQUE(company_id, receipt_number, EXTRACT(YEAR FROM receipt_date))
   *
   * @param companyId company ID
   * @param receiptNumber receipt number
   * @param receiptDate receipt date (year extracted for uniqueness check)
   * @param excludeId receipt ID to exclude (for updates, null for creates)
   * @return true if duplicate exists, false otherwise
   */
  @Query(
      "SELECT COUNT(ar) > 0 FROM ARPayment ar WHERE ar.companyId = :companyId "
          + "AND ar.receiptNumber = :receiptNumber "
          + "AND ar.receiptDate >= :yearStart AND ar.receiptDate < :yearEnd "
          + "AND (:excludeId IS NULL OR ar.id != :excludeId)")
  boolean existsByCompanyIdAndReceiptNumberAndYear(
      @Param("companyId") Long companyId,
      @Param("receiptNumber") String receiptNumber,
      @Param("yearStart") LocalDate yearStart,
      @Param("yearEnd") LocalDate yearEnd,
      @Param("excludeId") UUID excludeId);

  /**
   * Find receipt IDs matching search term using native PostgreSQL unaccent function.
   *
   * @param companyId company ID to filter by
   * @param searchTerm search term (will be matched against receipt_number, reference, and payee)
   * @return list of receipt IDs matching the search
   */
  @Query(
      value =
          "SELECT id FROM ar_payments WHERE company_id = :companyId "
              + "AND (unaccent_search(LOWER(receipt_number)) LIKE '%' || LOWER(unaccent_search(:searchTerm)) || '%' "
              + "OR unaccent_search(LOWER(reference)) LIKE '%' || LOWER(unaccent_search(:searchTerm)) || '%' "
              + "OR unaccent_search(LOWER(payee)) LIKE '%' || LOWER(unaccent_search(:searchTerm)) || '%')",
      nativeQuery = true)
  List<UUID> findIdsByCompanyIdAndSearchTerm(
      @Param("companyId") Long companyId, @Param("searchTerm") String searchTerm);
}
