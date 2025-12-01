package com.accounting.service;

import com.accounting.dto.ARStatementDetailedDTO;
import com.accounting.dto.ARStatementSummaryDTO;
import java.time.LocalDate;

/**
 * Service interface for AR statement calculation (summary and detailed views).
 */
public interface ARStatementCalculationService {

  /**
   * Generate summary statement for customer (invoice-level aggregation).
   *
   * @param customerId customer ID
   * @param asOfDate as-of date for statement (defaults to today if null)
   * @return summary statement DTO
   */
  ARStatementSummaryDTO generateSummaryStatement(Long customerId, LocalDate asOfDate);

  /**
   * Generate detailed statement for customer (transaction-level with receipts/credits).
   *
   * @param customerId customer ID
   * @param asOfDate as-of date for statement (defaults to today if null)
   * @return detailed statement DTO
   */
  ARStatementDetailedDTO generateDetailedStatement(Long customerId, LocalDate asOfDate);
}

