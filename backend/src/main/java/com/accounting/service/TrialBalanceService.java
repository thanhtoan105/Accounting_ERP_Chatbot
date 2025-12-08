package com.accounting.service;

import java.util.UUID;

import org.springframework.data.domain.Pageable;

import com.accounting.dto.DrillDownResponseDTO;
import com.accounting.dto.TrialBalanceResponseDTO;
import com.accounting.dto.TrialBalanceValidationDTO;
import com.accounting.enums.AmountType;

/**
 * Service for generating Trial Balance reports (S06-DN).
 * Handles calculation of account balances (opening, period activity, closing),
 * drill-down to voucher level, and export operations.
 */
public interface TrialBalanceService {

  /**
   * Generate trial balance data for a specific period.
   * Calculates opening balances (sum of all prior periods), period activity,
   * and closing balances (opening + period activity) for all accounts.
   *
   * @param periodId period ID to generate trial balance for
   * @return trial balance response with account balances and totals
   * @throws org.springframework.web.server.ResponseStatusException if period not found (404) or validation fails (400)
   */
  TrialBalanceResponseDTO getTrialBalanceData(UUID periodId);

  /**
   * Export trial balance report to Excel format.
   * Generates an Excel file with account balances and proper formatting.
   *
   * @param periodId period ID to export trial balance for
   * @return Excel file as byte array
   * @throws org.springframework.web.server.ResponseStatusException if period not found (404) or export fails (500)
   */
  byte[] exportToExcel(UUID periodId);

  /**
   * Get drill-down vouchers for a specific account and amount type.
   * Returns paginated list of vouchers that contribute to the selected amount cell
   * in the Trial Balance table.
   *
   * @param periodId   period ID
   * @param accountId  account ID to drill down into
   * @param amountType type of amount column clicked (opening/period/closing, debit/credit)
   * @param pageable   pagination and sorting parameters
   * @return paginated drill-down response with voucher details
   * @throws org.springframework.web.server.ResponseStatusException if period or account not found
   */
  DrillDownResponseDTO getDrillDownVouchers(UUID periodId, Long accountId, AmountType amountType, Pageable pageable);

  /**
   * Validate trial balance data before export.
   * Checks if the GL is balanced and period status is valid for export.
   *
   * @param periodId period ID to validate
   * @return validation result with errors if any
   */
  TrialBalanceValidationDTO validateForExport(UUID periodId);

  /**
   * Export trial balance report to PDF format with TT200 layout.
   * Includes company logo, footer with timestamp and hash, DRAFT watermark if period is open.
   *
   * @param periodId   period ID to export trial balance for
   * @param snapshotId optional snapshot ID for reproducibility (may be null)
   * @return PDF file as byte array
   * @throws org.springframework.web.server.ResponseStatusException if validation fails or export error
   */
  byte[] exportToPdf(UUID periodId, UUID snapshotId);
}
