package com.accounting.service;


import org.springframework.web.multipart.MultipartFile;

import com.accounting.dto.ImportResultDTO;

/**
 * Service interface for PurchaseBill batch import operations.
 * Supports Excel (.xlsx, .xls) format with validated template.
 */
public interface PurchaseBillImportService {

  /**
   * Import purchase bills from Excel file.
   * Validates all rows before saving (atomic transaction: all valid rows or none).
   * Auto-adds unknown suppliers as draft suppliers pending confirmation.
   * Creates audit log entry for each imported row.
   *
   * @param file Excel file to import
   * @return import result with success count, error count, error details, and error report ID
   */
  ImportResultDTO importBills(MultipartFile file);

  /**
   * Generate Excel template for purchase bill import.
   * Returns template with headers and example data.
   *
   * @return Excel file bytes
   */
  byte[] generateTemplate();
}
