package com.accounting.service;

import com.accounting.entity.ARStatementHistory;
import java.util.List;

/**
 * Service interface for AR statement export (PDF/Excel).
 */
public interface ARStatementExportService {

  /**
   * Export statement to PDF or Excel format.
   *
   * @param statement statement DTO (summary or detailed)
   * @param format export format ("PDF" or "EXCEL")
   * @param statementFormat statement format (SUMMARY or DETAILED)
   * @return byte array of exported file
   */
  byte[] exportStatement(Object statement, String format, ARStatementHistory.StatementFormat statementFormat);

  /**
   * Batch export statements for multiple customers as ZIP.
   *
   * @param customerIds list of customer IDs
   * @param format export format ("PDF" or "EXCEL")
   * @param statementFormat statement format (SUMMARY or DETAILED)
   * @param asOfDate as-of date
   * @return byte array of ZIP file
   */
  byte[] batchExportStatements(
      List<Long> customerIds,
      String format,
      ARStatementHistory.StatementFormat statementFormat,
      java.time.LocalDate asOfDate);
}

