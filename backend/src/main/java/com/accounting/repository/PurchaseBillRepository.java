package com.accounting.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.accounting.entity.PurchaseBill;
import com.accounting.entity.PurchaseBillStatus;

public interface PurchaseBillRepository
    extends JpaRepository<PurchaseBill, UUID>, JpaSpecificationExecutor<PurchaseBill> {

  /**
   * Find all purchase bills for a company.
   *
   * @param companyId company ID
   * @return list of purchase bills
   */
  List<PurchaseBill> findByCompanyId(Long companyId);

  /**
   * Find purchase bill by company ID and bill ID.
   *
   * @param companyId company ID
   * @param billId    bill ID
   * @return optional purchase bill
   */
  Optional<PurchaseBill> findByCompanyIdAndId(Long companyId, UUID billId);

  /**
   * Find purchase bills by company and status.
   *
   * @param companyId company ID
   * @param status    bill status
   * @return list of purchase bills
   */
  List<PurchaseBill> findByCompanyIdAndStatus(Long companyId, PurchaseBillStatus status);

  /**
   * Find purchase bills by company and date range.
   *
   * @param companyId company ID
   * @param dateFrom  start date (inclusive)
   * @param dateTo    end date (inclusive)
   * @return list of purchase bills
   */
  List<PurchaseBill> findByCompanyIdAndBillDateBetween(
      Long companyId, LocalDate dateFrom, LocalDate dateTo);

  /**
   * Check if purchase bill exists with duplicate supplier+bill number+year combination.
   * Unique constraint: UNIQUE(company_id, supplier_id, bill_number, EXTRACT(YEAR FROM bill_date))
   *
   * @param companyId  company ID
   * @param supplierId supplier ID
   * @param billNumber bill number
   * @param billDate   bill date (year extracted for uniqueness check)
   * @param excludeId  bill ID to exclude (for updates, null for creates)
   * @return true if duplicate exists, false otherwise
   */
  @Query(
      "SELECT COUNT(pb) > 0 FROM PurchaseBill pb WHERE pb.companyId = :companyId "
          + "AND pb.supplierId = :supplierId "
          + "AND pb.billNumber = :billNumber "
          + "AND pb.billDate >= :yearStart AND pb.billDate < :yearEnd "
          + "AND (:excludeId IS NULL OR pb.id != :excludeId)")
  boolean existsByCompanyIdAndSupplierIdAndBillNumberAndYear(
      @Param("companyId") Long companyId,
      @Param("supplierId") Long supplierId,
      @Param("billNumber") String billNumber,
      @Param("yearStart") LocalDate yearStart,
      @Param("yearEnd") LocalDate yearEnd,
      @Param("excludeId") UUID excludeId);

  /**
   * Check if purchase bill exists with duplicate supplier+bill number+bill date combination.
   *
   * @param companyId  company ID
   * @param supplierId supplier ID
   * @param billNumber bill number
   * @param billDate   bill date
   * @param excludeId  bill ID to exclude (for updates, null for creates)
   * @return true if duplicate exists, false otherwise
   */
  @Query(
      "SELECT COUNT(pb) > 0 FROM PurchaseBill pb WHERE pb.companyId = :companyId "
          + "AND pb.supplierId = :supplierId "
          + "AND pb.billNumber = :billNumber "
          + "AND pb.billDate = :billDate "
          + "AND (:excludeId IS NULL OR pb.id != :excludeId)")
  boolean existsByCompanyIdAndSupplierIdAndBillNumberAndBillDate(
      @Param("companyId") Long companyId,
      @Param("supplierId") Long supplierId,
      @Param("billNumber") String billNumber,
      @Param("billDate") LocalDate billDate,
      @Param("excludeId") UUID excludeId);

  /**
   * Find purchase bill IDs matching search term using native PostgreSQL unaccent function.
   * This method uses the unaccent_search() function for Vietnamese text matching.
   *
   * @param companyId  company ID to filter by
   * @param searchTerm search term (will be matched against bill_number and reference)
   * @return list of purchase bill IDs matching the search
   */
  @Query(
      value =
          "SELECT id FROM purchase_bills WHERE company_id = :companyId "
              + "AND (unaccent_search(LOWER(bill_number)) LIKE '%' || LOWER(unaccent_search(:searchTerm)) || '%' "
              + "OR unaccent_search(LOWER(reference)) LIKE '%' || LOWER(unaccent_search(:searchTerm)) || '%')",
      nativeQuery = true)
  List<UUID> findIdsByCompanyIdAndSearchTerm(
      @Param("companyId") Long companyId, @Param("searchTerm") String searchTerm);

}
