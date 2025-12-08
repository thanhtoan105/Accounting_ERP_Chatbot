package com.accounting.repository;

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

import com.accounting.entity.ARStatementDispute;

/**
 * Repository for ARStatementDispute entity with company-scoped queries.
 */
@Repository
public interface ARStatementDisputeRepository
        extends JpaRepository<ARStatementDispute, UUID>,
        JpaSpecificationExecutor<ARStatementDispute> {

    /**
     * Find dispute by ID with company scope.
     */
    @Query("SELECT d FROM ARStatementDispute d WHERE d.id = :id AND d.companyId = :companyId")
    Optional<ARStatementDispute> findByIdAndCompanyId(
            @Param("id") UUID id, @Param("companyId") Long companyId);

    /**
     * Find disputes by reconciliation ID with company scope.
     */
    @Query("SELECT d FROM ARStatementDispute d WHERE d.reconciliationId = :reconciliationId AND d.companyId = :companyId ORDER BY d.createdAt DESC")
    List<ARStatementDispute> findByReconciliationIdAndCompanyId(
            @Param("reconciliationId") UUID reconciliationId,
            @Param("companyId") Long companyId);

    /**
     * Find disputes by invoice ID with company scope.
     */
    @Query("SELECT d FROM ARStatementDispute d WHERE d.documentId = :invoiceId AND d.companyId = :companyId ORDER BY d.createdAt DESC")
    List<ARStatementDispute> findByInvoiceIdAndCompanyId(
            @Param("invoiceId") UUID invoiceId, @Param("companyId") Long companyId);

    /**
     * Find disputes by status with company scope.
     */
    @Query("SELECT d FROM ARStatementDispute d WHERE d.status = :status AND d.companyId = :companyId ORDER BY d.createdAt DESC")
    Page<ARStatementDispute> findByStatusAndCompanyId(
            @Param("status") ARStatementDispute.DisputeStatus status,
            @Param("companyId") Long companyId,
            Pageable pageable);

    /**
     * Find disputes by customer (via invoice) with filters.
     */
    @Query("SELECT d FROM ARStatementDispute d JOIN SalesInvoice i ON d.documentId = i.id WHERE i.customerId = :customerId AND d.companyId = :companyId AND (:status IS NULL OR d.status = :status) AND (:dateFrom IS NULL OR d.createdAt >= :dateFrom) AND (:dateTo IS NULL OR d.createdAt <= :dateTo) ORDER BY d.createdAt DESC")
    Page<ARStatementDispute> findByCustomerIdAndFilters(
            @Param("customerId") Long customerId,
            @Param("companyId") Long companyId,
            @Param("status") ARStatementDispute.DisputeStatus status,
            @Param("dateFrom") Instant dateFrom,
            @Param("dateTo") Instant dateTo,
            Pageable pageable);

    /**
     * Find all open disputes for a customer.
     */
    @Query("SELECT d FROM ARStatementDispute d JOIN SalesInvoice i ON d.documentId = i.id WHERE i.customerId = :customerId AND d.companyId = :companyId AND d.status = 'OPEN' ORDER BY d.createdAt DESC")
    List<ARStatementDispute> findOpenDisputesByCustomerId(
            @Param("customerId") Long customerId, @Param("companyId") Long companyId);
}
