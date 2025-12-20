package com.accounting.repository.reconciliation;

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

import com.accounting.entity.reconciliation.BankReconciliation;
import com.accounting.entity.reconciliation.ReconciliationStatus;

/**
 * Repository for BankReconciliation entities.
 * Provides persistence for reconciliation sessions with company scoping.
 */
public interface BankReconciliationRepository
        extends JpaRepository<BankReconciliation, UUID>, JpaSpecificationExecutor<BankReconciliation> {

    /**
     * Find all reconciliations for a company.
     *
     * @param companyId company ID
     * @param pageable  pagination settings
     * @return page of reconciliations
     */
    Page<BankReconciliation> findByCompanyId(Long companyId, Pageable pageable);

    /**
     * Find reconciliation by company and ID.
     *
     * @param companyId company ID
     * @param id        reconciliation ID
     * @return reconciliation if exists
     */
    Optional<BankReconciliation> findByCompanyIdAndId(Long companyId, UUID id);

    /**
     * Find all reconciliations for a specific bank account.
     *
     * @param companyId     company ID
     * @param bankAccountId bank account ID
     * @param pageable      pagination settings
     * @return page of reconciliations
     */
    Page<BankReconciliation> findByCompanyIdAndBankAccountId(Long companyId, Long bankAccountId, Pageable pageable);

    /**
     * Find reconciliations by status.
     *
     * @param companyId company ID
     * @param status    reconciliation status
     * @param pageable  pagination settings
     * @return page of reconciliations
     */
    Page<BankReconciliation> findByCompanyIdAndStatus(Long companyId, ReconciliationStatus status, Pageable pageable);

    /**
     * Check for overlapping reconciliation period (duplicate detection).
     *
     * @param companyId     company ID
     * @param bankAccountId bank account ID
     * @param periodStart   statement period start
     * @param periodEnd     statement period end
     * @return true if overlapping reconciliation exists
     */
    @Query("SELECT COUNT(r) > 0 FROM BankReconciliation r "
            + "WHERE r.companyId = :companyId AND r.bankAccountId = :bankAccountId "
            + "AND r.statementPeriodStart <= :periodEnd AND r.statementPeriodEnd >= :periodStart")
    boolean existsOverlappingPeriod(
            @Param("companyId") Long companyId,
            @Param("bankAccountId") Long bankAccountId,
            @Param("periodStart") LocalDate periodStart,
            @Param("periodEnd") LocalDate periodEnd);

    /**
     * Check for duplicate file import by hash.
     *
     * @param companyId company ID
     * @param fileHash  SHA256 hash of statement file
     * @return true if file was already imported
     */
    boolean existsByCompanyIdAndStatementFileHash(Long companyId, String fileHash);

    /**
     * Find reconciliation by file hash (for duplicate detection).
     *
     * @param companyId company ID
     * @param fileHash  SHA256 hash of statement file
     * @return reconciliation if exists
     */
    Optional<BankReconciliation> findByCompanyIdAndStatementFileHash(Long companyId, String fileHash);

    /**
     * Find the most recent reconciliation for a bank account.
     *
     * @param companyId     company ID
     * @param bankAccountId bank account ID
     * @return most recent reconciliation if exists
     */
    @Query("SELECT r FROM BankReconciliation r "
            + "WHERE r.companyId = :companyId AND r.bankAccountId = :bankAccountId "
            + "ORDER BY r.statementPeriodEnd DESC LIMIT 1")
    Optional<BankReconciliation> findMostRecentByBankAccountId(
            @Param("companyId") Long companyId,
            @Param("bankAccountId") Long bankAccountId);

    /**
     * Count reconciliations by status for a company.
     *
     * @param companyId company ID
     * @param status    reconciliation status
     * @return count
     */
    long countByCompanyIdAndStatus(Long companyId, ReconciliationStatus status);

    /**
     * Find all in-progress reconciliations for a company (for dashboard).
     *
     * @param companyId company ID
     * @return list of in-progress reconciliations
     */
    List<BankReconciliation> findByCompanyIdAndStatus(Long companyId, ReconciliationStatus status);
}
