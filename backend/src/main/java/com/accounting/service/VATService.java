package com.accounting.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.accounting.dto.InputVATReportDTO;
import com.accounting.dto.VATCorrectionCreateRequest;
import com.accounting.dto.VATCorrectionDTO;
import com.accounting.dto.VATValidationResultDTO;
import com.accounting.entity.PurchaseBill;
import com.accounting.entity.VatRate;

/**
 * Service interface for VAT validation, reporting, and corrections.
 */
public interface VATService {

  /**
   * Validate VAT rate for a line item.
   *
   * @param rate      VAT rate to validate
   * @param companyId company ID for default VAT rate check
   * @return validation result with warnings if override
   */
  VATValidationResultDTO validateVATRate(VatRate rate, Long companyId);

  /**
   * Validate VAT sum on document matches sum of line-level VAT.
   *
   * @param bill purchase bill to validate
   * @return validation result with error if mismatch >1,000₫
   */
  VATValidationResultDTO validateVATSum(PurchaseBill bill);

  /**
   * Validate VAT sum on sales invoice matches sum of line-level VAT.
   *
   * @param invoice sales invoice to validate
   * @return validation result with error if mismatch >1,000₫
   */
  VATValidationResultDTO validateVATSum(com.accounting.entity.SalesInvoice invoice);

  /**
   * Validate VAT ratio (vatAmount / amount) is between 0% and 100%.
   *
   * @param amount    base amount
   * @param vatAmount VAT amount
   * @return validation result with error if ratio is negative or over 100%
   */
  VATValidationResultDTO validateVATRatio(BigDecimal amount, BigDecimal vatAmount);

  /**
   * Map VAT to GL account 3331 (Input VAT) for voucher posting.
   *
   * @param bill purchase bill to map VAT for
   * @return calculated VAT amount for GL mapping
   */
  BigDecimal mapVATToGL(PurchaseBill bill);

  /**
   * Generate input VAT report by period/supplier/class.
   *
   * @param periodId   period ID (optional)
   * @param supplierId supplier ID (optional)
   * @param vatClass   VAT class filter (optional)
   * @param filters    additional filters
   * @return report DTO with aggregated VAT data
   */
  InputVATReportDTO generateInputVATReport(
      UUID periodId, Long supplierId, String vatClass, Map<String, Object> filters);

  /**
   * Export input VAT report to PDF or Excel format.
   *
   * @param reportId report ID
   * @param format   export format (PDF or EXCEL)
   * @return byte array of exported file
   */
  byte[] exportInputVATReport(UUID reportId, String format);

  /**
   * Create manual VAT correction.
   *
   * @param billId     bill ID
   * @param lineItemId line item ID (nullable for bill-level correction)
   * @param oldAmount  old VAT amount
   * @param newAmount  new VAT amount
   * @param reason     correction reason
   * @return correction DTO
   */
  VATCorrectionDTO createVATCorrection(VATCorrectionCreateRequest request);

  /**
   * Approve VAT correction.
   *
   * @param correctionId correction ID
   * @param approverId   approver user ID
   * @return correction DTO
   */
  VATCorrectionDTO approveVATCorrection(UUID correctionId, Long approverId);

  /**
   * Get VAT corrections for a bill with filters.
   *
   * @param billId  bill ID
   * @param filters filter parameters
   * @return list of correction DTOs
   */
  List<VATCorrectionDTO> getVATCorrections(UUID billId, Map<String, Object> filters);
}
