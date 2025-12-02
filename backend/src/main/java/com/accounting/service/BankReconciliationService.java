package com.accounting.service;

import com.accounting.dto.reconciliation.BankReconciliationDTO;
import com.accounting.dto.reconciliation.BankReconciliationListDTO;
import com.accounting.dto.reconciliation.BankStatementLineDTO;
import com.accounting.dto.reconciliation.CreateAdjustmentRequestDTO;
import com.accounting.dto.reconciliation.CreateReconciliationRequestDTO;
import com.accounting.dto.reconciliation.MatchRequestDTO;
import com.accounting.dto.reconciliation.ReconciliationAdjustmentDTO;
import com.accounting.entity.reconciliation.MatchStatus;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Service for bank reconciliation operations.
 * Provides CRUD operations, matching, adjustments, and completion workflow.
 *
 * <p>
 * AC6.5-01 through AC6.5-09: Full reconciliation workflow
 */
public interface BankReconciliationService {

    // ========== Reconciliation CRUD ==========

    /**
     * Create a new bank reconciliation.
     *
     * @param request creation request
     * @return created reconciliation
     */
    BankReconciliationDTO createReconciliation(CreateReconciliationRequestDTO request);

    /**
     * Get reconciliation by ID with full details.
     *
     * @param reconciliationId reconciliation ID
     * @return reconciliation with statement lines and adjustments
     */
    BankReconciliationDTO getReconciliation(UUID reconciliationId);

    /**
     * List reconciliations for the current company.
     *
     * @param bankAccountId bank account ID filter (optional)
     * @param status        status filter (optional)
     * @param pageable      pagination settings
     * @return page of reconciliations
     */
    Page<BankReconciliationListDTO> listReconciliations(Long bankAccountId, String status, Pageable pageable);

    /**
     * Delete a reconciliation (only if not completed).
     *
     * @param reconciliationId reconciliation ID
     */
    void deleteReconciliation(UUID reconciliationId);

    // ========== Statement Lines ==========

    /**
     * Get statement lines for a reconciliation.
     *
     * @param reconciliationId reconciliation ID
     * @param matchStatus      match status filter (optional)
     * @param pageable         pagination settings
     * @return page of statement lines
     */
    Page<BankStatementLineDTO> getStatementLines(UUID reconciliationId, MatchStatus matchStatus, Pageable pageable);

    /**
     * Get a single statement line.
     *
     * @param reconciliationId reconciliation ID
     * @param lineId           statement line ID
     * @return statement line
     */
    BankStatementLineDTO getStatementLine(UUID reconciliationId, UUID lineId);

    // ========== Matching Operations ==========

    /**
     * Manually match a statement line to a voucher.
     *
     * @param reconciliationId reconciliation ID
     * @param matchRequest     match request with line ID, voucher ID, and notes
     * @return updated statement line
     */
    BankStatementLineDTO manualMatch(UUID reconciliationId, MatchRequestDTO matchRequest);

    /**
     * Unmatch a statement line.
     *
     * @param reconciliationId reconciliation ID
     * @param lineId           statement line ID
     * @return updated statement line
     */
    BankStatementLineDTO unmatch(UUID reconciliationId, UUID lineId);

    /**
     * Update notes for a statement line.
     *
     * @param reconciliationId reconciliation ID
     * @param lineId           statement line ID
     * @param notes            new notes
     * @return updated statement line
     */
    BankStatementLineDTO updateLineNotes(UUID reconciliationId, UUID lineId, String notes);

    // ========== Adjustments ==========

    /**
     * Create an adjustment for the reconciliation.
     *
     * @param reconciliationId reconciliation ID
     * @param request          adjustment creation request
     * @return created adjustment
     */
    ReconciliationAdjustmentDTO createAdjustment(UUID reconciliationId, CreateAdjustmentRequestDTO request);

    /**
     * Approve an adjustment (Chief Accountant only).
     *
     * @param reconciliationId reconciliation ID
     * @param adjustmentId     adjustment ID
     * @return updated adjustment
     */
    ReconciliationAdjustmentDTO approveAdjustment(UUID reconciliationId, UUID adjustmentId);

    /**
     * Reject an adjustment (Chief Accountant only).
     *
     * @param reconciliationId reconciliation ID
     * @param adjustmentId     adjustment ID
     * @param reason           rejection reason
     * @return updated adjustment
     */
    ReconciliationAdjustmentDTO rejectAdjustment(UUID reconciliationId, UUID adjustmentId, String reason);

    /**
     * Post an approved adjustment (creates voucher).
     *
     * @param reconciliationId reconciliation ID
     * @param adjustmentId     adjustment ID
     * @return updated adjustment with voucher ID
     */
    ReconciliationAdjustmentDTO postAdjustment(UUID reconciliationId, UUID adjustmentId);

    /**
     * Delete an adjustment (only if pending).
     *
     * @param reconciliationId reconciliation ID
     * @param adjustmentId     adjustment ID
     */
    void deleteAdjustment(UUID reconciliationId, UUID adjustmentId);

    // ========== Completion ==========

    /**
     * Complete the reconciliation.
     * Validates all lines matched/adjusted and balance reconciled.
     * Updates bank account last reconciled date/balance.
     *
     * @param reconciliationId reconciliation ID
     * @param notes            completion notes
     * @return completed reconciliation
     */
    BankReconciliationDTO completeReconciliation(UUID reconciliationId, String notes);

    /**
     * Reopen a completed reconciliation (Chief Accountant only).
     *
     * @param reconciliationId reconciliation ID
     * @return reopened reconciliation
     */
    BankReconciliationDTO reopenReconciliation(UUID reconciliationId);

    // ========== Export ==========

    /**
     * Export reconciliation to Excel.
     *
     * @param reconciliationId reconciliation ID
     * @return Excel file bytes
     */
    byte[] exportToExcel(UUID reconciliationId);

    /**
     * Export reconciliation to PDF.
     *
     * @param reconciliationId reconciliation ID
     * @return PDF file bytes
     */
    byte[] exportToPdf(UUID reconciliationId);
}
