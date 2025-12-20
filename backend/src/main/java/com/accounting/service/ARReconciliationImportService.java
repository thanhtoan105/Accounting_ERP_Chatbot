package com.accounting.service;

import org.springframework.web.multipart.MultipartFile;

import com.accounting.dto.ARReconciliationImportDTO;

/**
 * Service interface for AR reconciliation import (CSV parsing and mismatch detection).
 */
public interface ARReconciliationImportService {

  /**
   * Import customer-provided reconciliation CSV and compare with system data.
   *
   * @param customerId customer ID
   * @param file CSV file with InvoiceNumber, CustomerAmount, CustomerPayment, Notes columns
   * @return reconciliation import result with matched/mismatched counts and mismatch list
   */
  ARReconciliationImportDTO importReconciliation(Long customerId, MultipartFile file);
}
