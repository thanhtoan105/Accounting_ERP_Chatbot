package com.accounting.service;

import com.accounting.dto.ARPaymentCreateRequest;
import com.accounting.dto.ARPaymentDTO;
import com.accounting.dto.ARPaymentListDTO;
import com.accounting.dto.ReceiptAllocationRequest;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Service interface for AR receipt operations (create, allocate, post, reverse).
 * Mirrors APPaymentService but for customer receipts.
 */
public interface ReceiptService {

  /**
   * Find all receipts with pagination, sorting, and filtering.
   *
   * @param pageable pagination and sorting information
   * @param filters filter parameters (customer, status, dateFrom, dateTo, search, standalone)
   * @return page of receipt list DTOs
   */
  Page<ARPaymentListDTO> findAll(Pageable pageable, Map<String, Object> filters);

  /**
   * Find receipt by ID.
   *
   * @param receiptId receipt ID
   * @return receipt DTO with allocations
   */
  ARPaymentDTO findById(UUID receiptId);

  /**
   * Create new receipt with validation and audit logging.
   *
   * @param request receipt creation request
   * @return created receipt DTO
   */
  ARPaymentDTO create(ARPaymentCreateRequest request);

  /**
   * Update existing receipt (DRAFT only).
   *
   * @param receiptId receipt ID
   * @param request receipt update request
   * @return updated receipt DTO
   */
  ARPaymentDTO update(UUID receiptId, ARPaymentCreateRequest request);

  /**
   * Delete receipt (DRAFT only).
   *
   * @param receiptId receipt ID
   */
  void delete(UUID receiptId);

  /**
   * Allocate receipt to invoices (allows allocation to one or many invoices).
   *
   * @param receiptId receipt ID
   * @param allocations list of allocation requests
   * @return updated receipt DTO with allocations
   */
  ARPaymentDTO allocateInvoices(UUID receiptId, List<ReceiptAllocationRequest> allocations);

  /**
   * Update allocations before posting (DRAFT only).
   *
   * @param receiptId receipt ID
   * @param allocations list of allocation requests
   * @return updated receipt DTO with allocations
   */
  ARPaymentDTO updateAllocations(UUID receiptId, List<ReceiptAllocationRequest> allocations);

  /**
   * Post receipt (generates voucher Dr Bank/Cash 111/112, Cr AR 131, updates invoice statuses).
   *
   * @param receiptId receipt ID
   * @return posted receipt DTO
   */
  ARPaymentDTO postReceipt(UUID receiptId);

  /**
   * Reverse POSTED receipt (generates linked reversal voucher, maintains audit cross-references).
   *
   * @param receiptId receipt ID
   * @param reason mandatory reversal reason
   * @return reversed receipt DTO
   */
  ARPaymentDTO reverseReceipt(UUID receiptId, String reason);

  /**
   * Get open invoices for customer (for picker/allocation).
   *
   * @param customerId customer ID
   * @return list of open/unpaid invoices
   */
  List<Map<String, Object>> getOpenInvoicesForCustomer(Long customerId);

  /**
   * Generate receipt number (auto-generated, format RCP-{YYYY}-{seq}).
   *
   * @param receiptDate receipt date (year extracted for numbering)
   * @return generated receipt number
   */
  String generateReceiptNumber(LocalDate receiptDate);
}
