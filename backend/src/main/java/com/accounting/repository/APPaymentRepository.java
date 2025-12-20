package com.accounting.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.accounting.entity.APPayment;
import com.accounting.entity.PaymentStatus;

public interface APPaymentRepository
    extends JpaRepository<APPayment, UUID>, JpaSpecificationExecutor<APPayment> {

  /**
   * Find all payments for a company.
   *
   * @param companyId company ID
   * @return list of payments
   */
  List<APPayment> findByCompanyId(Long companyId);

  /**
   * Find payment by company ID and payment ID.
   *
   * @param companyId company ID
   * @param paymentId payment ID
   * @return optional payment
   */
  Optional<APPayment> findByCompanyIdAndId(Long companyId, UUID paymentId);

  /**
   * Find payments by company and status.
   *
   * @param companyId company ID
   * @param status payment status
   * @return list of payments
   */
  List<APPayment> findByCompanyIdAndStatus(Long companyId, PaymentStatus status);

  /**
   * Find payments by company and date range.
   *
   * @param companyId company ID
   * @param dateFrom start date (inclusive)
   * @param dateTo end date (inclusive)
   * @return list of payments
   */
  List<APPayment> findByCompanyIdAndPaymentDateBetween(
      Long companyId, LocalDate dateFrom, LocalDate dateTo);

  /**
   * Find payments by company and supplier.
   *
   * @param companyId company ID
   * @param supplierId supplier ID
   * @return list of payments
   */
  List<APPayment> findByCompanyIdAndSupplierId(Long companyId, Long supplierId);

  /**
   * Check if payment exists with duplicate payment number+year combination.
   * Unique constraint: UNIQUE(company_id, payment_number, EXTRACT(YEAR FROM payment_date))
   *
   * @param companyId company ID
   * @param paymentNumber payment number
   * @param paymentDate payment date (year extracted for uniqueness check)
   * @param excludeId payment ID to exclude (for updates, null for creates)
   * @return true if duplicate exists, false otherwise
   */
  @Query(
      "SELECT COUNT(ap) > 0 FROM APPayment ap WHERE ap.companyId = :companyId "
          + "AND ap.paymentNumber = :paymentNumber "
          + "AND ap.paymentDate >= :yearStart AND ap.paymentDate < :yearEnd "
          + "AND (:excludeId IS NULL OR ap.id != :excludeId)")
  boolean existsByCompanyIdAndPaymentNumberAndYear(
      @Param("companyId") Long companyId,
      @Param("paymentNumber") String paymentNumber,
      @Param("yearStart") LocalDate yearStart,
      @Param("yearEnd") LocalDate yearEnd,
      @Param("excludeId") UUID excludeId);

  /**
   * Find payment IDs matching search term using native PostgreSQL unaccent function.
   *
   * @param companyId company ID to filter by
   * @param searchTerm search term (will be matched against payment_number and reference)
   * @return list of payment IDs matching the search
   */
  @Query(
      value =
          "SELECT id FROM ap_payments WHERE company_id = :companyId "
              + "AND (unaccent_search(LOWER(payment_number)) LIKE '%' || LOWER(unaccent_search(:searchTerm)) || '%' "
              + "OR unaccent_search(LOWER(reference)) LIKE '%' || LOWER(unaccent_search(:searchTerm)) || '%' "
              + "OR unaccent_search(LOWER(payee)) LIKE '%' || LOWER(unaccent_search(:searchTerm)) || '%')",
      nativeQuery = true)
  List<UUID> findIdsByCompanyIdAndSearchTerm(
      @Param("companyId") Long companyId, @Param("searchTerm") String searchTerm);
}
