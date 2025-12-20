package com.accounting.service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.accounting.dto.SalesInvoiceCreateRequest;
import com.accounting.dto.SalesInvoiceLineDTO;
import com.accounting.dto.SalesInvoiceValidationResult;

/**
 * Service for validating sales invoices before creation or update.
 * Performs business rule validations including invoice number uniqueness,
 * date validation, line item validation, VAT sum validation, and duplicate
 * checks.
 */
public interface SalesInvoiceValidationService {

    /**
     * Validate a sales invoice create/update request.
     *
     * @param request   sales invoice create request with line items
     * @param invoiceId invoice ID for updates (null for creates)
     * @return validation result with errors (if any)
     */
    SalesInvoiceValidationResult validate(SalesInvoiceCreateRequest request, UUID invoiceId);

    /**
     * Validate invoice number uniqueness per customer per year.
     *
     * @param customerId    customer ID
     * @param invoiceNumber invoice number
     * @param invoiceDate   invoice date
     * @param companyId     company ID
     * @param invoiceId     invoice ID to exclude (for updates, null for creates)
     * @return true if invoice number is unique, false otherwise
     */
    boolean validateInvoiceNumber(Long customerId, String invoiceNumber, LocalDate invoiceDate, Long companyId,
            UUID invoiceId);

    /**
     * Validate dates (future dates disabled, period validation).
     *
     * @param invoiceDate invoice date
     * @param dueDate     due date
     * @return validation result with errors (if any)
     */
    SalesInvoiceValidationResult validateDates(LocalDate invoiceDate, LocalDate dueDate);

    /**
     * Validate line items (leaf/postable accounts, positive amounts, required
     * dimensions).
     *
     * @param lines     list of line items
     * @param companyId company ID
     * @return validation result with errors (if any)
     */
    SalesInvoiceValidationResult validateLineItems(List<SalesInvoiceLineDTO> lines, Long companyId);

    /**
     * Validate VAT sum match between header and line items (tolerance: 1,000₫).
     *
     * @param headerVatAmount header VAT amount
     * @param lineVatSum      sum of line item VAT amounts
     * @return validation result with errors (if any)
     */
    SalesInvoiceValidationResult validateVATSum(java.math.BigDecimal headerVatAmount, java.math.BigDecimal lineVatSum);

    /**
     * Validate duplicate customer+invoice/date combination.
     *
     * @param customerId    customer ID
     * @param invoiceNumber invoice number
     * @param invoiceDate   invoice date
     * @param companyId     company ID
     * @param invoiceId     invoice ID to exclude (for updates, null for creates)
     * @return true if no duplicate found, false otherwise
     */
    boolean validateDuplicate(Long customerId, String invoiceNumber, LocalDate invoiceDate, Long companyId,
            UUID invoiceId);

    /**
     * Validate required dimensions for line items.
     *
     * @param lines     list of line items
     * @param companyId company ID
     * @return validation result with errors (if any)
     */
    SalesInvoiceValidationResult validateRequiredDimensions(
            List<SalesInvoiceLineDTO> lines, Long companyId);
}
