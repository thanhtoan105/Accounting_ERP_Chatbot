package com.accounting.service;

import com.accounting.dto.VoucherCreateRequest;
import com.accounting.dto.VoucherValidationResult;

/**
 * Service for validating vouchers before creation or update.
 * Performs business rule validations including double-entry balance,
 * leaf-only account validation, and required dimensions.
 */
public interface VoucherValidationService {

  /**
   * Validate a voucher create/update request.
   *
   * @param request voucher create request with line items
   * @return validation result with errors (if any)
   */
  VoucherValidationResult validate(VoucherCreateRequest request);
}

