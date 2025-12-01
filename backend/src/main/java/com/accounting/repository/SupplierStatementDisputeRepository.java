package com.accounting.repository;

import com.accounting.entity.SupplierStatementDispute;
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

public interface SupplierStatementDisputeRepository
        extends JpaRepository<SupplierStatementDispute, UUID>,
        JpaSpecificationExecutor<SupplierStatementDispute> {

    /**
     * Find all disputes for a company.
     *
     * @param companyId company ID
     * @param pageable  pagination
     * @return page of disputes
     */
    Page<SupplierStatementDispute> findByCompanyId(Long companyId, Pageable pageable);

    /**
     * Find dispute by company ID and dispute ID.
     *
     * @param companyId company ID
     * @param disputeId dispute ID
     * @return optional dispute
     */
    Optional<SupplierStatementDispute> findByCompanyIdAndId(Long companyId, UUID disputeId);

    /**
     * Find disputes for a supplier within a company.
     *
     * @param companyId company ID
     * @param partyId   supplier ID (partyId field)
     * @param pageable  pagination
     * @return page of disputes
     */
    Page<SupplierStatementDispute> findByCompanyIdAndPartyId(
            Long companyId, Long partyId, Pageable pageable);

    /**
     * Find disputes by company and status.
     *
     * @param companyId company ID
     * @param status    dispute status
     * @param pageable  pagination
     * @return page of disputes
     */
    Page<SupplierStatementDispute> findByCompanyIdAndStatus(
            Long companyId, SupplierStatementDispute.DisputeStatus status, Pageable pageable);

    /**
     * Find disputes by company, supplier, and status.
     *
     * @param companyId company ID
     * @param partyId   supplier ID (partyId field)
     * @param status    dispute status
     * @param pageable  pagination
     * @return page of disputes
     */
    @Query("SELECT d FROM SupplierStatementDispute d "
            + "WHERE d.companyId = :companyId AND d.partyId = :partyId "
            + "AND d.status = :status")
    Page<SupplierStatementDispute> findByCompanyIdAndPartyIdAndStatus(
            @Param("companyId") Long companyId,
            @Param("partyId") Long partyId,
            @Param("status") String status,
            Pageable pageable);

    /**
     * Find all open disputes for a supplier.
     *
     * @param companyId  company ID
     * @param supplierId supplier ID
     * @return list of open disputes
     */
    @Query("SELECT d FROM SupplierStatementDispute d "
            + "WHERE d.companyId = :companyId AND d.partyId = :supplierId "
            + "AND d.status IN ('OPEN', 'IN_PROGRESS')")
    List<SupplierStatementDispute> findOpenDisputesByCompanyIdAndSupplierId(
            @Param("companyId") Long companyId, @Param("supplierId") Long supplierId);

    /**
     * Find disputes by company and date range.
     *
     * @param companyId company ID
     * @param startDate start date (inclusive)
     * @param endDate   end date (inclusive)
     * @param pageable  pagination
     * @return page of disputes
     */
    Page<SupplierStatementDispute> findByCompanyIdAndCreatedAtBetween(
            Long companyId, Instant startDate, Instant endDate, Pageable pageable);

    /**
     * Count open disputes for a supplier.
     *
     * @param companyId  company ID
     * @param supplierId supplier ID
     * @return count of open disputes
     */
    @Query("SELECT COUNT(d) FROM SupplierStatementDispute d "
            + "WHERE d.companyId = :companyId AND d.partyId = :supplierId "
            + "AND d.status IN ('OPEN', 'IN_PROGRESS')")
    Long countOpenDisputesByCompanyIdAndSupplierId(
            @Param("companyId") Long companyId, @Param("supplierId") Long supplierId);

    /**
     * Find disputes for a specific bill.
     *
     * @param companyId company ID
     * @param billId    bill ID
     * @return list of disputes for the bill
     */
    @Query("SELECT d FROM SupplierStatementDispute d WHERE d.companyId = :companyId AND d.documentId = :billId")
    List<SupplierStatementDispute> findByCompanyIdAndBillId(
            @Param("companyId") Long companyId, @Param("billId") UUID billId);
}
