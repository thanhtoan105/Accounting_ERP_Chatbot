package com.accounting.service;

import java.math.BigDecimal;
import java.util.List;

import com.accounting.dto.ReceiptAllocationRequest;
import com.accounting.dto.ReceiptValidationResult;
import com.accounting.entity.ARPayment;

/**
 * Service for receipt validation including overpayment prevention,
 * balance validation, allocation validation, and standalone receipt validation.
 */
public interface ReceiptValidationService {

  /**
   * Validate if customer has open/unpaid invoices (for linked receipts).
   *
   * @param customerId customer ID
   * @return validation result
   */
  ReceiptValidationResult validateCustomerHasOpenInvoices(Long customerId);

  /**
   * Validate receipt allocations (no overpayment, allocated amounts don't exceed invoice remaining balances).
   *
   * @param allocations list of allocation requests
   * @param receiptAmount total receipt amount
   * @return validation result with field-level errors
   */
  ReceiptValidationResult validateAllocations(
      List<ReceiptAllocationRequest> allocations, BigDecimal receiptAmount);

  /**
   * Validate account balance (sufficient balance, overdraft warning/block).
   *
   * @param accountId bank account ID
   * @param receiptAmount receipt amount
   * @return validation result with warnings/errors
   */
  ReceiptValidationResult validateAccountBalance(Long accountId, BigDecimal receiptAmount);

  /**
   * Validate standalone receipt (admin-only, logs warning tag).
   *
   * @param receipt receipt entity
   * @param userId current user ID
   * @return validation result
   */
  ReceiptValidationResult validateStandaloneReceipt(ARPayment receipt, Long userId);

  /**
   * Validate receipt proof requirement (required if above threshold).
   *
   * @param receiptAmount receipt amount
   * @param proofUrl receipt proof URL
   * @return validation result
   */
  ReceiptValidationResult validateReceiptProof(BigDecimal receiptAmount, String proofUrl);
}
