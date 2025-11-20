package com.accounting.repository;

import com.accounting.entity.ApprovalWorkflow;
import com.accounting.entity.ApprovalWorkflowStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository for ApprovalWorkflow entity.
 * Handles database operations for purchase bill approval workflows.
 * All queries are automatically filtered by company_id through CompanyScopeAspect.
 */
@Repository
public interface ApprovalWorkflowRepository extends JpaRepository<ApprovalWorkflow, UUID> {

  /**
   * Find approval workflow by purchase bill ID.
   * Returns the most recent workflow for the bill (for cases of resubmission).
   */
  @Query(
      "SELECT aw FROM ApprovalWorkflow aw "
          + "WHERE aw.purchaseBillId = :billId "
          + "ORDER BY aw.createdAt DESC")
  List<ApprovalWorkflow> findByPurchaseBillId(@Param("billId") UUID billId);

  /**
   * Find the latest approval workflow for a purchase bill.
   */
  @Query(
      "SELECT aw FROM ApprovalWorkflow aw "
          + "WHERE aw.purchaseBillId = :billId "
          + "ORDER BY aw.createdAt DESC "
          + "LIMIT 1")
  Optional<ApprovalWorkflow> findLatestByPurchaseBillId(@Param("billId") UUID billId);

  /**
   * Find all pending approval workflows for the current company.
   * Used to display approval queue for Chief Accountant/CFO.
   */
  @Query(
      "SELECT aw FROM ApprovalWorkflow aw "
          + "WHERE aw.status = :status "
          + "ORDER BY aw.createdAt ASC")
  List<ApprovalWorkflow> findByStatus(@Param("status") ApprovalWorkflowStatus status);

  /**
   * Find pending workflows created before a specific timestamp.
   * Used for escalation notification (e.g., pending > 48 hours).
   */
  @Query(
      "SELECT aw FROM ApprovalWorkflow aw "
          + "WHERE aw.status = 'PENDING' "
          + "AND aw.createdAt < :timestamp "
          + "ORDER BY aw.createdAt ASC")
  List<ApprovalWorkflow> findPendingOlderThan(@Param("timestamp") java.time.Instant timestamp);

  /**
   * Count pending approvals for a company (for notification badge).
   */
  @Query("SELECT COUNT(aw) FROM ApprovalWorkflow aw WHERE aw.status = 'PENDING'")
  long countPending();
}
