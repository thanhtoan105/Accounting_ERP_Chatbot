package com.accounting.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.accounting.dto.ARStatementDisputeDTO;
import com.accounting.entity.ARStatementDispute;

/**
 * Service interface for AR dispute management.
 */
public interface ARDisputeService {

  /**
   * Get disputes with filters.
   *
   * @param customerId optional customer filter
   * @param status optional status filter
   * @param dateFrom optional start date filter
   * @param dateTo optional end date filter
   * @param pageable pagination
   * @return page of disputes
   */
  Page<ARStatementDisputeDTO> getDisputes(
      Long customerId,
      ARStatementDispute.DisputeStatus status,
      Instant dateFrom,
      Instant dateTo,
      Pageable pageable);

  /**
   * Get dispute by ID.
   *
   * @param disputeId dispute ID
   * @return dispute DTO
   */
  ARStatementDisputeDTO getDisputeById(UUID disputeId);

  /**
   * Resolve dispute with resolution notes.
   *
   * @param disputeId dispute ID
   * @param resolutionNotes resolution notes
   */
  void resolveDispute(UUID disputeId, String resolutionNotes);

  /**
   * Get dispute history for an invoice.
   *
   * @param invoiceId invoice ID
   * @return list of disputes for the invoice
   */
  List<ARStatementDisputeDTO> getDisputeHistory(UUID invoiceId);

  /**
   * Get reconciliation notes for a customer (aggregated resolved disputes).
   *
   * @param customerId customer ID
   * @return list of reconciliation notes
   */
  List<ARStatementDisputeDTO> getReconciliationNotes(Long customerId);
}
