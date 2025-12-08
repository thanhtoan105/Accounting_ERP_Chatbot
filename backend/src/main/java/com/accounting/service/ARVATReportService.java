package com.accounting.service;

import java.util.Map;
import java.util.UUID;

import com.accounting.dto.OutputVATReportDTO;

/**
 * Service interface for AR Output VAT report generation and export.
 * Handles ND123-compliant output VAT reports for sales invoices.
 */
public interface ARVATReportService {

  /**
   * Generate output VAT report by period/customer/VAT class.
   *
   * @param periodId   period ID (optional)
   * @param customerId customer ID (optional)
   * @param vatClass   VAT class filter (optional)
   * @param filters    additional filters
   * @return report DTO with aggregated VAT data
   */
  OutputVATReportDTO generateVATReport(
      UUID periodId, Long customerId, String vatClass, Map<String, Object> filters);

  /**
   * Export output VAT report to Excel format (ND123 compliant).
   *
   * @param reportId report ID
   * @param format   export format (EXCEL)
   * @return byte array of exported file
   */
  byte[] exportVATReport(UUID reportId, String format);
}
