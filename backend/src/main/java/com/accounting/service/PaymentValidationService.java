package com.accounting.service;

import java.math.BigDecimal;
import java.util.List;

import com.accounting.dto.PaymentAllocationRequest;
import com.accounting.dto.PaymentValidationResult;
import com.accounting.entity.APPayment;

/**
 * Service for payment validation including overpayment prevention,
 * balance validation, allocation validation, and standalone payment validation.
 */
public interface PaymentValidationService {

  /**
   * Validate if supplier has open/unpaid bills (for linked payments).
   *
   * @param supplierId supplier ID
   * @return validation result
   */
  PaymentValidationResult validateSupplierHasOpenBills(Long supplierId);

  /**
   * Validate payment allocations (no overpayment, allocated amounts don't exceed bill remaining balances).
   *
   * @param allocations list of allocation requests
   * @param paymentAmount total payment amount
   * @return validation result with field-level errors
   */
  PaymentValidationResult validateAllocations(
      List<PaymentAllocationRequest> allocations, BigDecimal paymentAmount);

  /**
   * Validate account balance (sufficient balance, overdraft warning/block).
   *
   * @param accountId bank account ID
   * @param paymentAmount payment amount
   * @return validation result with warnings/errors
   */
  PaymentValidationResult validateAccountBalance(Long accountId, BigDecimal paymentAmount);

  /**
   * Validate standalone payment (admin-only, logs warning tag).
   *
   * @param payment payment entity
   * @param userId current user ID
   * @return validation result
   */
  PaymentValidationResult validateStandalonePayment(APPayment payment, Long userId);

  /**
   * Validate payment proof requirement (required if above threshold).
   *
   * @param paymentAmount payment amount
   * @param proofUrl payment proof URL
   * @return validation result
   */
  PaymentValidationResult validatePaymentProof(BigDecimal paymentAmount, String proofUrl);

  /**
   * Suggest quick add supplier if non-master, logs ad hoc tag.
   *
   * @param supplierName supplier name
   * @return validation result with suggestion
   */
  PaymentValidationResult suggestQuickAddSupplier(String supplierName);
}
