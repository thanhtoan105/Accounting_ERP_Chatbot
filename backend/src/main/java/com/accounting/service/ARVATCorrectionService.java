package com.accounting.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.accounting.dto.ARVATCorrectionCreateRequest;
import com.accounting.dto.ARVATCorrectionDTO;

/**
 * Service interface for AR VAT correction operations.
 * Handles creation, approval, and rejection of VAT corrections for sales invoices.
 */
public interface ARVATCorrectionService {

  /**
   * Create a new VAT correction for a sales invoice.
   *
   * @param request correction request with invoice ID, line item (optional), new VAT amount, and reason
   * @return created correction DTO
   */
  ARVATCorrectionDTO createCorrection(ARVATCorrectionCreateRequest request);

  /**
   * Approve a pending VAT correction.
   * Applies the correction to the invoice and updates voucher if needed.
   *
   * @param correctionId correction ID to approve
   * @param approverId   approver user ID (optional, defaults to current user)
   * @return approved correction DTO
   */
  ARVATCorrectionDTO approveCorrection(UUID correctionId, Long approverId);

  /**
   * Reject a pending VAT correction.
   *
   * @param correctionId correction ID to reject
   * @param reason       rejection reason
   * @return rejected correction DTO
   */
  ARVATCorrectionDTO rejectCorrection(UUID correctionId, String reason);

  /**
   * Get VAT corrections for an invoice with optional filters.
   *
   * @param invoiceId invoice ID
   * @param filters   optional filters (status, startDate, endDate, correctedById)
   * @return list of correction DTOs
   */
  List<ARVATCorrectionDTO> getCorrections(UUID invoiceId, Map<String, Object> filters);

  /**
   * Get VAT corrections with pagination.
   *
   * @param pageable  pagination parameters
   * @param invoiceId optional invoice ID filter
   * @param status    optional status filter
   * @return paginated list of correction DTOs
   */
  Page<ARVATCorrectionDTO> getCorrections(
      Pageable pageable, UUID invoiceId, com.accounting.entity.ARVATCorrection.Status status);
}
