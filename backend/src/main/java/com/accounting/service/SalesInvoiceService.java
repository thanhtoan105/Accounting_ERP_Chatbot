package com.accounting.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.accounting.dto.SalesInvoiceCreateRequest;
import com.accounting.dto.SalesInvoiceDTO;
import com.accounting.dto.SalesInvoiceListDTO;
import com.accounting.entity.SalesInvoiceStatus;

public interface SalesInvoiceService {

    /**
     * Find all sales invoices with pagination, filtering, and sorting support.
     *
     * @param pageable   pagination and sorting parameters
     * @param customerId filter by customer ID (optional)
     * @param status     filter by status (optional)
     * @param dateFrom   filter by date from (optional)
     * @param dateTo     filter by date to (optional)
     * @param search     search term for invoice number or reference (optional,
     *                   supports Vietnamese unaccented matching)
     * @return paginated list of sales invoices
     */
    Page<SalesInvoiceListDTO> findAll(
            Pageable pageable,
            Long customerId,
            SalesInvoiceStatus status,
            LocalDate dateFrom,
            LocalDate dateTo,
            String search);

    /**
     * Search sales invoices by number or reference with Vietnamese unaccented
     * support.
     *
     * @param searchTerm search term for invoice number or reference
     * @return list of matching sales invoices
     */
    List<SalesInvoiceDTO> search(String searchTerm);

    /**
     * Get sales invoice by ID.
     *
     * @param invoiceId sales invoice ID
     * @return optional sales invoice DTO
     */
    Optional<SalesInvoiceDTO> findById(UUID invoiceId);

    /**
     * Create a new sales invoice with line items.
     *
     * @param request sales invoice create request with line items
     * @return created sales invoice DTO
     * @throws org.springframework.web.server.ResponseStatusException if validation
     *                                                                fails
     */
    SalesInvoiceDTO create(SalesInvoiceCreateRequest request);

    /**
     * Update an existing draft sales invoice.
     *
     * @param invoiceId sales invoice ID
     * @param request   sales invoice update request with line items
     * @return updated sales invoice DTO
     * @throws org.springframework.web.server.ResponseStatusException if validation
     *                                                                fails or
     *                                                                invoice is not
     *                                                                draft
     */
    SalesInvoiceDTO update(UUID invoiceId, SalesInvoiceCreateRequest request);

    /**
     * Delete sales invoice with validation (only DRAFT status, creator-only unless
     * admin).
     *
     * @param invoiceId sales invoice ID
     * @param reason    deletion reason (required)
     * @param request   HTTP request for audit logging
     * @throws org.springframework.web.server.ResponseStatusException if validation
     *                                                                fails
     */
    void delete(UUID invoiceId, String reason, jakarta.servlet.http.HttpServletRequest request);

    /**
     * Save draft sales invoice (autosave functionality).
     *
     * @param request sales invoice create/update request
     * @return saved draft sales invoice DTO
     */
    SalesInvoiceDTO saveDraft(SalesInvoiceCreateRequest request);

    /**
     * Recover draft sales invoice (recoverable by creator/admin).
     *
     * @param invoiceId sales invoice ID
     * @return recovered sales invoice DTO
     */
    SalesInvoiceDTO recoverDraft(UUID invoiceId);

    /**
     * Get list of recoverable drafts for current user/admin.
     *
     * @return list of draft sales invoices
     */
    List<SalesInvoiceDTO> getDrafts();

    /**
     * Check if duplicate sales invoice exists (customer+invoice number+date
     * combination).
     *
     * @param customerId    customer ID
     * @param invoiceNumber invoice number
     * @param invoiceDate   invoice date
     * @return true if duplicate exists, false otherwise
     */
    boolean checkDuplicate(Long customerId, String invoiceNumber, LocalDate invoiceDate);

    /**
     * Create a credit note (negative invoice) that references an original invoice.
     * Credit notes have inverted GL splits and are linked to the original invoice.
     *
     * @param originalInvoiceId ID of the original invoice (must be POSTED)
     * @param request           credit note create request with line items
     * @return created credit note DTO
     * @throws org.springframework.web.server.ResponseStatusException if validation
     *                                                                fails or original invoice is not POSTED
     */
    SalesInvoiceDTO createCreditNote(UUID originalInvoiceId, SalesInvoiceCreateRequest request);
}
