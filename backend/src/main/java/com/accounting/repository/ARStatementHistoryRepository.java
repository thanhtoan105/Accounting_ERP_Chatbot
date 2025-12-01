package com.accounting.repository;

import com.accounting.entity.ARStatementHistory;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository for ARStatementHistory entity with company-scoped queries.
 */
@Repository
public interface ARStatementHistoryRepository
        extends JpaRepository<ARStatementHistory, UUID>,
        JpaSpecificationExecutor<ARStatementHistory> {

    /**
     * Find statement by ID with company scope.
     */
    @Query("SELECT s FROM ARStatementHistory s WHERE s.id = :id AND s.companyId = :companyId")
    Optional<ARStatementHistory> findByIdAndCompanyId(
            @Param("id") UUID id, @Param("companyId") Long companyId);

    /**
     * Find all statements for a customer with company scope.
     */
    @Query("SELECT s FROM ARStatementHistory s WHERE s.partyId = :customerId AND s.companyId = :companyId ORDER BY s.generatedAt DESC")
    Page<ARStatementHistory> findByCustomerIdAndCompanyId(
            @Param("customerId") Long customerId,
            @Param("companyId") Long companyId,
            Pageable pageable);

    /**
     * Find statements by date range with company scope.
     */
    @Query("SELECT s FROM ARStatementHistory s WHERE s.companyId = :companyId AND s.generatedAt >= :startDate AND s.generatedAt <= :endDate ORDER BY s.generatedAt DESC")
    Page<ARStatementHistory> findByCompanyIdAndDateRange(
            @Param("companyId") Long companyId,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate,
            Pageable pageable);

    /**
     * Find statement by statement number with company scope.
     */
    @Query("SELECT s FROM ARStatementHistory s WHERE s.statementNumber = :statementNumber AND s.companyId = :companyId")
    Optional<ARStatementHistory> findByStatementNumberAndCompanyId(
            @Param("statementNumber") String statementNumber,
            @Param("companyId") Long companyId);

    /**
     * Find all statements for multiple customers (batch export).
     */
    @Query("SELECT s FROM ARStatementHistory s WHERE s.partyId IN :customerIds AND s.companyId = :companyId ORDER BY s.generatedAt DESC")
    List<ARStatementHistory> findByCustomerIdsAndCompanyId(
            @Param("customerIds") List<Long> customerIds, @Param("companyId") Long companyId);
}
