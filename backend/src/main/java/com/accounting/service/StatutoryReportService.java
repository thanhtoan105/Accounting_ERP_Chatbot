package com.accounting.service;

import com.accounting.dto.report.DetailedLedgerDTO;
import com.accounting.dto.report.StatutoryReportDTO;
import java.util.UUID;

/**
 * Service for generating TT200 statutory financial reports.
 * Supports B01-DN (Balance Sheet), B02-DN (Income Statement),
 * B03-DN (Cash Flow Statement), and F01 (Detailed Ledger).
 */
public interface StatutoryReportService {

  /**
   * Generate B01-DN Balance Sheet for a period.
   *
   * @param periodId the accounting period ID
   * @param comparisonPeriodId optional comparison period ID for variance analysis
   * @return statutory report with all balance sheet lines
   */
  StatutoryReportDTO generateBalanceSheet(UUID periodId, UUID comparisonPeriodId);

  /**
   * Generate B02-DN Income Statement for a period.
   *
   * @param periodId the accounting period ID
   * @param comparisonPeriodId optional comparison period ID for variance analysis
   * @return statutory report with all income statement lines
   */
  StatutoryReportDTO generateIncomeStatement(UUID periodId, UUID comparisonPeriodId);

  /**
   * Generate B03-DN Cash Flow Statement for a period.
   * Uses direct method as per TT200 requirements.
   *
   * @param periodId the accounting period ID
   * @return statutory report with cash flow lines
   */
  StatutoryReportDTO generateCashFlowStatement(UUID periodId);

  /**
   * Generate F01 Detailed Ledger for specific accounts.
   *
   * @param accountCodes array of account codes to include
   * @param periodId the accounting period ID
   * @param subsidiaryType optional subsidiary type ('CUSTOMER', 'SUPPLIER', 'ITEM')
   * @param subsidiaryId optional subsidiary ID for filtering
   * @return detailed ledger with transaction-level data
   */
  DetailedLedgerDTO generateDetailedLedger(
      String[] accountCodes, UUID periodId, String subsidiaryType, Long subsidiaryId);

  /**
   * Validate report data before export.
   * Checks for NULL values in required lines and GL balance.
   *
   * @param periodId the accounting period ID
   * @param reportType the report type ('B01', 'B02', 'B03')
   * @return validation result with any errors/warnings
   */
  ReportValidationResult validateForExport(UUID periodId, String reportType);

  /**
   * Validation result containing any errors or warnings.
   */
  record ReportValidationResult(
      boolean valid,
      java.util.List<String> errors,
      java.util.List<String> warnings,
      boolean isBalanced,
      boolean isDraft
  ) {}
}
