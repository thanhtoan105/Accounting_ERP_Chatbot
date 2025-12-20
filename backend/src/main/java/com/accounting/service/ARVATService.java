package com.accounting.service;

import java.math.BigDecimal;
import java.util.List;

import com.accounting.dto.VATValidationResultDTO;
import com.accounting.dto.VoucherEntryLineRequest;
import com.accounting.entity.SalesInvoice;
import com.accounting.entity.VatRate;

/**
 * Service interface for AR (Accounts Receivable) VAT validation, calculation, and GL split generation.
 * Handles output VAT for sales invoices with revenue accounts (5xx) and output VAT account (33311).
 */
public interface ARVATService {

  /**
   * Validate VAT rate for a line item.
   *
   * @param rate      VAT rate to validate
   * @param companyId company ID for default VAT rate check
   * @return validation result with warnings if override detected
   */
  VATValidationResultDTO validateVATRate(VatRate rate, Long companyId);

  /**
   * Calculate VAT amount for a line item with rounding to nearest 100 VND.
   *
   * @param lineTotal line total amount (before VAT)
   * @param vatRate   VAT rate
   * @return rounded VAT amount (to nearest 100 VND per Circular 200)
   */
  BigDecimal calculateVAT(BigDecimal lineTotal, VatRate vatRate);

  /**
   * Validate VAT sum on invoice matches sum of line-level VAT.
   *
   * @param invoice sales invoice to validate
   * @return validation result with error if mismatch ≥ 1000 VND
   */
  VATValidationResultDTO validateVATSum(SalesInvoice invoice);

  /**
   * Generate GL split entries for invoice posting.
   * Creates: Dr 131 (AR), Cr 5xx (revenue per line), Cr 33311 (output VAT).
   *
   * @param invoice sales invoice to generate GL splits for
   * @return list of voucher entry lines for voucher creation
   */
  List<VoucherEntryLineRequest> generateGLSplit(SalesInvoice invoice);

  /**
   * Generate GL split entries for credit note (inverted).
   * Creates: Cr 131 (reverses AR), Dr 5xx (reverses revenue), Dr 33311 (reverses VAT).
   *
   * @param creditNote      credit note invoice
   * @param originalInvoice original invoice being credited
   * @return list of inverted voucher entry lines for voucher creation
   */
  List<VoucherEntryLineRequest> generateCreditNoteGLSplit(
      SalesInvoice creditNote, SalesInvoice originalInvoice);
}
