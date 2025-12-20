package com.accounting.service;

import org.springframework.web.multipart.MultipartFile;

import com.accounting.dto.ImportResultDTO;

/**
 * Service interface for AP Payment batch import operations.
 * Supports Excel (.xlsx, .xls) format with validated template.
 */
public interface PaymentImportService {

  /**
   * Import payments from Excel file.
   * Validates all rows before saving (atomic transaction: all valid rows or none).
   * Auto-adds unknown suppliers as draft suppliers pending confirmation.
   * Creates audit log entry for each imported row.
   *
   * @param file Excel file to import
   * @return import result with success count, error count, error details, and error report ID
   */
  ImportResultDTO importPayments(MultipartFile file);

  /**
   * Generate Excel template for payment import.
   * Returns template with headers and example data.
   *
   * @return Excel file bytes
   */
  byte[] generateTemplate();
}
