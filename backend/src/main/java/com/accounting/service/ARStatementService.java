package com.accounting.service;

import com.accounting.dto.ARStatementHistoryDTO;
import com.accounting.entity.ARStatementHistory;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Service interface for AR statement operations (generation, export, email, history).
 */
public interface ARStatementService {

  /**
   * Get statement for customer (summary or detailed format).
   *
   * @param customerId customer ID
   * @param format statement format (SUMMARY or DETAILED)
   * @param asOfDate as-of date (optional, defaults to today)
   * @return statement DTO (summary or detailed)
   */
  Object getStatement(
      Long customerId,
      ARStatementHistory.StatementFormat format,
      LocalDate asOfDate);

  /**
   * Get statement and return history ID for audit tracking.
   *
   * @param customerId customer ID
   * @param format statement format (SUMMARY or DETAILED)
   * @param asOfDate as-of date (optional, defaults to today)
   * @return statement result with history ID
   */
  StatementResult getStatementWithHistory(
      Long customerId,
      ARStatementHistory.StatementFormat format,
      LocalDate asOfDate);

  /**
   * Result class containing statement and history ID.
   */
  class StatementResult {
    private final Object statement;
    private final UUID historyId;

    public StatementResult(Object statement, UUID historyId) {
      this.statement = statement;
      this.historyId = historyId;
    }

    public Object getStatement() {
      return statement;
    }

    public UUID getHistoryId() {
      return historyId;
    }
  }

  /**
   * Export statement to PDF or Excel format.
   *
   * @param customerId customer ID
   * @param format export format (PDF or EXCEL)
   * @param statementFormat statement format (SUMMARY or DETAILED)
   * @param asOfDate as-of date
   * @return export result with file data and history ID
   */
  ExportResult exportStatement(
      Long customerId,
      String format,
      ARStatementHistory.StatementFormat statementFormat,
      LocalDate asOfDate);

  /**
   * Result class containing export data and history ID.
   */
  class ExportResult {
    private final byte[] data;
    private final UUID historyId;

    public ExportResult(byte[] data, UUID historyId) {
      this.data = data;
      this.historyId = historyId;
    }

    public byte[] getData() {
      return data;
    }

    public UUID getHistoryId() {
      return historyId;
    }
  }

  /**
   * Send statement to customer via email.
   *
   * @param customerId customer ID
   * @param recipientEmail recipient email address
   * @param statementFormat statement format (SUMMARY or DETAILED)
   * @param asOfDate as-of date
   * @return history ID for audit tracking
   */
  UUID sendStatementToCustomer(
      Long customerId,
      String recipientEmail,
      ARStatementHistory.StatementFormat statementFormat,
      LocalDate asOfDate);

  /**
   * Batch export statements for multiple customers as ZIP.
   *
   * @param customerIds list of customer IDs
   * @param format export format (PDF or EXCEL)
   * @param statementFormat statement format (SUMMARY or DETAILED)
   * @param asOfDate as-of date
   * @return byte array of ZIP file
   */
  byte[] batchExportStatements(
      List<Long> customerIds,
      String format,
      ARStatementHistory.StatementFormat statementFormat,
      LocalDate asOfDate);

  /**
   * Get statement history for a customer.
   *
   * @param customerId customer ID
   * @param pageable pagination
   * @return page of statement history
   */
  Page<ARStatementHistoryDTO> getStatementHistory(Long customerId, Pageable pageable);

  /**
   * Regenerate statement from historical record.
   *
   * @param statementId statement history ID
   * @return regenerated statement DTO
   */
  Object regenerateStatement(UUID statementId);
}

