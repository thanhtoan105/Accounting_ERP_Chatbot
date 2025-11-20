package com.accounting.service;

import com.accounting.dto.DetailedStatementDTO;
import com.accounting.dto.ReconciliationResultDTO;
import com.accounting.dto.SupplierStatementDTO;
import com.accounting.dto.SupplierStatementDisputeDTO;
import com.accounting.dto.SupplierStatementHistoryDTO;
import com.accounting.dto.UpdateDisputeRequest;
import com.accounting.entity.SupplierStatementHistory;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

/**
 * Service interface for supplier statement generation, reconciliation, and dispute management.
 */
public interface SupplierStatementService {

  /**
   * Generate summary statement for supplier (by bill).
   *
   * @param supplierId supplier ID
   * @param startDate start date (inclusive)
   * @param endDate end date (inclusive)
   * @return supplier statement DTO
   */
  SupplierStatementDTO generateSummaryStatement(
      Long supplierId, LocalDate startDate, LocalDate endDate);

  /**
   * Generate detailed statement for supplier (by payment/event).
   *
   * @param supplierId supplier ID
   * @param startDate start date (inclusive)
   * @param endDate end date (inclusive)
   * @return detailed statement DTO
   */
  DetailedStatementDTO generateDetailedStatement(
      Long supplierId, LocalDate startDate, LocalDate endDate);

  /**
   * Get statement by ID.
   *
   * @param statementId statement ID
   * @return supplier statement DTO
   */
  SupplierStatementHistoryDTO getStatementById(UUID statementId);

  /**
   * Find all statements with pagination and filters.
   *
   * @param supplierId optional supplier filter
   * @param startDate optional start date filter
   * @param endDate optional end date filter
   * @param statementType optional statement type filter
   * @param pageable pagination
   * @return page of statement history
   */
  Page<SupplierStatementHistoryDTO> findAllStatements(
      Long supplierId,
      LocalDate startDate,
      LocalDate endDate,
      SupplierStatementHistory.StatementType statementType,
      Pageable pageable);

  /**
   * Export statement to PDF or Excel format.
   *
   * @param statementId statement ID
   * @param format export format (PDF or EXCEL)
   * @return byte array of exported file
   */
  byte[] exportStatement(UUID statementId, SupplierStatementHistory.ExportFormat format);

  /**
   * Send statement to supplier via email.
   *
   * @param statementId statement ID
   * @param recipientEmails list of recipient email addresses
   */
  void sendStatementToSupplier(UUID statementId, List<String> recipientEmails);

  /**
   * Send multiple statements as ZIP attachment.
   *
   * @param statementIds list of statement IDs
   * @param recipientEmails list of recipient email addresses
   */
  void sendBatchStatements(List<UUID> statementIds, List<String> recipientEmails);

  /**
   * Download multiple statements as ZIP archive.
   *
   * @param statementIds list of statement IDs
   * @return byte array of ZIP file
   */
  byte[] downloadStatementBatch(List<UUID> statementIds);

  /**
   * Import supplier-provided statement and compare with system data.
   *
   * @param supplierId supplier ID
   * @param file statement file (Excel/CSV)
   * @param format file format (EXCEL/CSV)
   * @return reconciliation result with matched/mismatched/missing/applied items
   */
  ReconciliationResultDTO importSupplierStatement(
      Long supplierId, MultipartFile file, String format);

  /**
   * Save reconciliation results and create dispute entries for mismatches.
   *
   * @param supplierId supplier ID
   * @param results reconciliation results
   * @param notes manual notes
   */
  void saveReconciliationResults(
      Long supplierId, ReconciliationResultDTO results, String notes);

  /**
   * Update dispute status and resolution.
   *
   * @param disputeId dispute ID
   * @param request update request with status and resolution notes
   */
  void updateDisputeLog(UUID disputeId, UpdateDisputeRequest request);

  /**
   * Get dispute by ID.
   *
   * @param disputeId dispute ID
   * @return dispute DTO
   */
  SupplierStatementDisputeDTO getDisputeById(UUID disputeId);

  /**
   * Find disputes with pagination and filters.
   *
   * @param supplierId optional supplier filter
   * @param status optional status filter
   * @param pageable pagination
   * @return page of disputes
   */
  Page<SupplierStatementDisputeDTO> findDisputes(
      Long supplierId, String status, Pageable pageable);

  /**
   * Get statement history for a supplier.
   *
   * @param supplierId supplier ID
   * @param pageable pagination
   * @return page of statement history
   */
  Page<SupplierStatementHistoryDTO> getStatementHistory(Long supplierId, Pageable pageable);
}

