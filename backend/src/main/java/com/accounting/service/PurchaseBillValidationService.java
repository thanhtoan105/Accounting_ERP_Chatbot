package com.accounting.service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.accounting.dto.PurchaseBillCreateRequest;
import com.accounting.dto.PurchaseBillValidationResult;

/**
 * Service for validating purchase bills before creation or update.
 * Performs business rule validations including bill number uniqueness,
 * date validation, line item validation, VAT sum validation, and duplicate checks.
 */
public interface PurchaseBillValidationService {

  /**
   * Validate a purchase bill create/update request.
   *
   * @param request purchase bill create request with line items
   * @param billId  bill ID for updates (null for creates)
   * @return validation result with errors (if any)
   */
  PurchaseBillValidationResult validate(PurchaseBillCreateRequest request, UUID billId);

  /**
   * Validate bill number uniqueness per supplier per year.
   *
   * @param supplierId supplier ID
   * @param billNumber bill number
   * @param billDate   bill date
   * @param companyId  company ID
   * @param billId     bill ID to exclude (for updates, null for creates)
   * @return true if bill number is unique, false otherwise
   */
  boolean validateBillNumber(Long supplierId, String billNumber, LocalDate billDate, Long companyId, UUID billId);

  /**
   * Validate dates (future dates disabled, period validation).
   *
   * @param billDate bill date
   * @param dueDate  due date
   * @return validation result with errors (if any)
   */
  PurchaseBillValidationResult validateDates(LocalDate billDate, LocalDate dueDate);

  /**
   * Validate line items (leaf/postable accounts, positive amounts, required dimensions).
   *
   * @param lines    list of line items
   * @param companyId company ID
   * @return validation result with errors (if any)
   */
  PurchaseBillValidationResult validateLineItems(List<com.accounting.dto.PurchaseBillLineDTO> lines, Long companyId);

  /**
   * Validate VAT sum match between header and line items (tolerance: 1,000₫).
   *
   * @param headerVatAmount header VAT amount
   * @param lineVatSum     sum of line item VAT amounts
   * @return validation result with errors (if any)
   */
  PurchaseBillValidationResult validateVATSum(java.math.BigDecimal headerVatAmount, java.math.BigDecimal lineVatSum);

  /**
   * Validate duplicate supplier+bill/date combination.
   *
   * @param supplierId supplier ID
   * @param billNumber bill number
   * @param billDate   bill date
   * @param companyId  company ID
   * @param billId     bill ID to exclude (for updates, null for creates)
   * @return true if no duplicate found, false otherwise
   */
  boolean validateDuplicate(Long supplierId, String billNumber, LocalDate billDate, Long companyId, UUID billId);

  /**
   * Validate required dimensions for line items.
   *
   * @param lines    list of line items
   * @param companyId company ID
   * @return validation result with errors (if any)
   */
  PurchaseBillValidationResult validateRequiredDimensions(
      List<com.accounting.dto.PurchaseBillLineDTO> lines, Long companyId);
}
