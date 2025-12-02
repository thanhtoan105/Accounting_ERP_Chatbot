package com.accounting.repository.reconciliation;

import com.accounting.entity.reconciliation.AdjustmentStatus;
import com.accounting.entity.reconciliation.ReconciliationAdjustment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repository for ReconciliationAdjustment entities.
 * Provides persistence for adjustment entries with approval workflow.
 */
public interface ReconciliationAdjustmentRepository extends JpaRepository<ReconciliationAdjustment, UUID> {

    /**
     * Find all adjustments for a reconciliation.
     *
     * @param reconciliationId reconciliation ID
     * @return list of adjustments
     */
    List<ReconciliationAdjustment> findByReconciliationId(UUID reconciliationId);

    /**
     * Find adjustment by ID within a reconciliation.
     *
     * @param reconciliationId reconciliation ID
     * @param adjustmentId     adjustment ID
     * @return adjustment if exists
     */
    Optional<ReconciliationAdjustment> findByReconciliationIdAndId(UUID reconciliationId, UUID adjustmentId);

    /**
     * Find adjustments by status for a reconciliation.
     *
     * @param reconciliationId reconciliation ID
     * @param status           adjustment status
     * @return list of adjustments
     */
    List<ReconciliationAdjustment> findByReconciliationIdAndStatus(UUID reconciliationId, AdjustmentStatus status);

    /**
     * Find pending adjustments across all reconciliations for a company.
     * Used for approval dashboard.
     *
     * @param companyId company ID
     * @param pageable  pagination settings
     * @return page of pending adjustments
     */
    @Query("SELECT a FROM ReconciliationAdjustment a "
            + "JOIN a.reconciliation r "
            + "WHERE r.companyId = :companyId AND a.status = 'PENDING' "
            + "ORDER BY a.createdAt ASC")
    Page<ReconciliationAdjustment> findPendingByCompanyId(@Param("companyId") Long companyId, Pageable pageable);

    /**
     * Count pending adjustments for a company (for badge/notification).
     *
     * @param companyId company ID
     * @return count of pending adjustments
     */
    @Query("SELECT COUNT(a) FROM ReconciliationAdjustment a "
            + "JOIN a.reconciliation r "
            + "WHERE r.companyId = :companyId AND a.status = 'PENDING'")
    long countPendingByCompanyId(@Param("companyId") Long companyId);

    /**
     * Count adjustments by status for a reconciliation.
     *
     * @param reconciliationId reconciliation ID
     * @param status           adjustment status
     * @return count
     */
    long countByReconciliationIdAndStatus(UUID reconciliationId, AdjustmentStatus status);

    /**
     * Check if reconciliation has any unposted adjustments (blocking completion).
     *
     * @param reconciliationId reconciliation ID
     * @return true if there are pending or approved (but not posted) adjustments
     */
    @Query("SELECT COUNT(a) > 0 FROM ReconciliationAdjustment a "
            + "WHERE a.reconciliation.id = :reconciliationId "
            + "AND a.status IN ('PENDING', 'APPROVED')")
    boolean hasUnpostedAdjustments(@Param("reconciliationId") UUID reconciliationId);

    /**
     * Find adjustments linked to a statement line.
     *
     * @param statementLineId statement line ID
     * @return list of adjustments
     */
    List<ReconciliationAdjustment> findByStatementLineId(UUID statementLineId);

    /**
     * Delete all adjustments for a reconciliation.
     *
     * @param reconciliationId reconciliation ID
     */
    void deleteByReconciliationId(UUID reconciliationId);
}
