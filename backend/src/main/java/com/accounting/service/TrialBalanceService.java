package com.accounting.service;

import com.accounting.dto.TrialBalanceResponseDTO;
import java.util.UUID;

/**
 * Service for generating Trial Balance reports (S06-DN).
 * Handles calculation of account balances (opening, period activity, closing) and Excel export.
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
}

