package com.accounting.repository;

import com.accounting.entity.SupplierStatementHistory;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SupplierStatementHistoryRepository
    extends JpaRepository<SupplierStatementHistory, UUID>,
        JpaSpecificationExecutor<SupplierStatementHistory> {

  /**
   * Find all statement history for a company.
   *
   * @param companyId company ID
   * @param pageable pagination
   * @return page of statement history
   */
  Page<SupplierStatementHistory> findByCompanyId(Long companyId, Pageable pageable);

  /**
   * Find statement history by company ID and statement ID.
   *
   * @param companyId company ID
   * @param statementId statement ID
   * @return optional statement history
   */
  Optional<SupplierStatementHistory> findByCompanyIdAndId(Long companyId, UUID statementId);

  /**
   * Find statement history for a supplier within a company.
   *
   * @param companyId company ID
   * @param supplierId supplier ID
   * @param pageable pagination
   * @return page of statement history
   */
  Page<SupplierStatementHistory> findByCompanyIdAndSupplierId(
      Long companyId, Long supplierId, Pageable pageable);

  /**
   * Find statement history by company and date range.
   *
   * @param companyId company ID
   * @param startDate start date (inclusive)
   * @param endDate end date (inclusive)
   * @param pageable pagination
   * @return page of statement history
   */
  Page<SupplierStatementHistory> findByCompanyIdAndGenerationDateBetween(
      Long companyId, Instant startDate, Instant endDate, Pageable pageable);

  /**
   * Find statement history by company, supplier, and statement type.
   *
   * @param companyId company ID
   * @param supplierId supplier ID
   * @param statementType statement type
   * @param pageable pagination
   * @return page of statement history
   */
  Page<SupplierStatementHistory> findByCompanyIdAndSupplierIdAndStatementType(
      Long companyId,
      Long supplierId,
      SupplierStatementHistory.StatementType statementType,
      Pageable pageable);

  /**
   * Find all statements by IDs for batch operations.
   *
   * @param companyId company ID
   * @param statementIds list of statement IDs
   * @return list of statement history
   */
  @Query(
      "SELECT s FROM SupplierStatementHistory s "
          + "WHERE s.companyId = :companyId AND s.id IN :statementIds")
  List<SupplierStatementHistory> findByCompanyIdAndIdIn(
      @Param("companyId") Long companyId, @Param("statementIds") List<UUID> statementIds);

  /**
   * Find statements by supplier and date range.
   *
   * @param companyId company ID
   * @param supplierId supplier ID
   * @param startDate start date (inclusive)
   * @param endDate end date (inclusive)
   * @return list of statement history
   */
  @Query(
      "SELECT s FROM SupplierStatementHistory s "
          + "WHERE s.companyId = :companyId AND s.supplierId = :supplierId "
          + "AND s.startDate >= :startDate AND s.endDate <= :endDate")
  List<SupplierStatementHistory> findByCompanyIdAndSupplierIdAndDateRange(
      @Param("companyId") Long companyId,
      @Param("supplierId") Long supplierId,
      @Param("startDate") LocalDate startDate,
      @Param("endDate") LocalDate endDate);
}

