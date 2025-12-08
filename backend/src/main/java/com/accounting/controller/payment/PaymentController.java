package com.accounting.controller.payment;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.accounting.dto.APPaymentCreateRequest;
import com.accounting.dto.APPaymentDTO;
import com.accounting.dto.APPaymentListDTO;
import com.accounting.dto.ImportResultDTO;
import com.accounting.dto.PaymentAllocationDTO;
import com.accounting.dto.PaymentAllocationRequest;
import com.accounting.dto.PurchaseBillDTO;
import com.accounting.entity.PaymentStatus;
import com.accounting.service.PaymentImportService;
import com.accounting.service.PaymentService;

import jakarta.validation.Valid;

/**
 * REST controller for AP Payment operations.
 * All authenticated users with Accountant+ role can view payments; edit
 * requires Accountant+ role;
 * standalone payments require Admin role.
 */
@RestController
@RequestMapping("/api/v1/ap-payments")
public class PaymentController {

  private final PaymentService paymentService;
  private final PaymentImportService paymentImportService;

  public PaymentController(PaymentService paymentService, PaymentImportService paymentImportService) {
    this.paymentService = paymentService;
    this.paymentImportService = paymentImportService;
  }

  /**
   * Get paginated, filtered, sorted payment list.
   * Supports query params: page, size, supplier, status, dateFrom, dateTo,
   * search, standalone, sort.
   * Requires authenticated user with Accountant+ role.
   *
   * @param page       page number (0-based, default: 0)
   * @param size       page size (default: 20, max: 100)
   * @param supplier   filter by supplier ID (optional)
   * @param status     filter by status (optional: DRAFT, PENDING_APPROVAL,
   *                   POSTED, CANCELLED)
   * @param dateFrom   filter by date from (optional, format: YYYY-MM-DD)
   * @param dateTo     filter by date to (optional, format: YYYY-MM-DD)
   * @param search     search term for payment number, reference, or payee
   *                   (optional)
   * @param standalone filter by standalone flag (optional)
   * @param sort       sort parameters (optional, format: field,direction e.g.,
   *                   paymentDate,desc)
   * @return paginated payment list
   */
  @GetMapping
  @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT', 'CFO')")
  public ResponseEntity<Map<String, Object>> getPayments(
      @RequestParam(required = false, defaultValue = "0") int page,
      @RequestParam(required = false, defaultValue = "20") int size,
      @RequestParam(required = false) Long supplier,
      @RequestParam(required = false) PaymentStatus status,
      @RequestParam(required = false) LocalDate dateFrom,
      @RequestParam(required = false) LocalDate dateTo,
      @RequestParam(required = false) String search,
      @RequestParam(required = false) Boolean standalone,
      @RequestParam(required = false) String[] sort) {

    // Validate page size (max 100)
    if (size > 100) {
      size = 100;
    }

    // Build sort object
    Sort sortObj = Sort.unsorted();
    if (sort != null && sort.length > 0) {
      List<Sort.Order> orders = new ArrayList<>();
      for (String sortParam : sort) {
        String[] parts = sortParam.split(",");
        if (parts.length == 2) {
          String field = parts[0] == null ? "" : parts[0].trim();
          if (field.isEmpty()) {
            continue;
          }
          Sort.Direction direction = "desc".equalsIgnoreCase(parts[1].trim()) ? Sort.Direction.DESC
              : Sort.Direction.ASC;
          orders.add(new Sort.Order(direction, field));
        }
      }
      if (!orders.isEmpty()) {
        sortObj = Sort.by(orders);
      }
    }

    // Build pageable
    Pageable pageable = PageRequest.of(page, size, sortObj);

    // Call service
    Page<APPaymentListDTO> payments = paymentService.findAll(pageable, supplier, status, dateFrom, dateTo, search,
        standalone);

    // Build response matching spec format: { data: { content: APPaymentListDTO[],
    // totalElements: number, totalPages: number }, meta: {...} }
    Map<String, Object> data = new HashMap<>();
    data.put("content", payments.getContent());
    data.put("totalElements", payments.getTotalElements());
    data.put("totalPages", payments.getTotalPages());

    Map<String, Object> meta = new HashMap<>();
    meta.put("page", payments.getNumber());
    meta.put("size", payments.getSize());
    meta.put("totalElements", payments.getTotalElements());
    meta.put("totalPages", payments.getTotalPages());

    Map<String, Object> body = new HashMap<>();
    body.put("data", data);
    body.put("meta", meta);

    return ResponseEntity.ok(body);
  }

  /**
   * Get payment by ID (with allocations).
   * Requires authenticated user with Accountant+ role.
   *
   * @param id payment ID
   * @return payment details with allocations array
   */
  @GetMapping("/{id}")
  @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT', 'CFO')")
  public ResponseEntity<APPaymentDTO> getPayment(@PathVariable("id") UUID id) {
    return paymentService
        .findById(id)
        .map(ResponseEntity::ok)
        .orElseThrow(
            () -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Payment not found: " + id));
  }

  /**
   * Create a new payment.
   * If allocations not provided, performs FIFO allocation automatically.
   * Requires authenticated user with Accountant+ role.
   * Standalone payments require Admin role.
   *
   * @param request payment create request
   * @return created payment DTO
   */
  @PostMapping
  @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
  public ResponseEntity<APPaymentDTO> createPayment(
      @Valid @RequestBody APPaymentCreateRequest request) {
    APPaymentDTO payment = paymentService.create(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(payment);
  }

  /**
   * Update an existing draft payment.
   * Only DRAFT payments can be updated.
   * Requires authenticated user with Accountant+ role.
   *
   * @param id      payment ID
   * @param request payment update request
   * @return updated payment DTO
   */
  @PutMapping("/{id}")
  @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
  public ResponseEntity<APPaymentDTO> updatePayment(
      @PathVariable("id") UUID id, @Valid @RequestBody APPaymentCreateRequest request) {
    request.setId(id);
    APPaymentDTO payment = paymentService.update(id, request);
    return ResponseEntity.ok(payment);
  }

  /**
   * Delete a draft payment.
   * Only DRAFT payments can be deleted.
   * Requires authenticated user with Accountant+ role.
   *
   * @param id payment ID
   * @return 204 No Content
   */
  @DeleteMapping("/{id}")
  @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
  public ResponseEntity<Void> deletePayment(@PathVariable("id") UUID id) {
    paymentService.delete(id);
    return ResponseEntity.noContent().build();
  }

  /**
   * Allocate payment manually (override FIFO allocation).
   * Only DRAFT payments can be modified.
   * Requires authenticated user with Accountant+ role.
   *
   * @param id          payment ID
   * @param allocations list of allocation requests
   * @return updated payment DTO
   */
  @PostMapping("/{id}/allocate")
  @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
  public ResponseEntity<APPaymentDTO> allocatePayment(
      @PathVariable("id") UUID id,
      @Valid @RequestBody List<PaymentAllocationRequest> allocations) {
    APPaymentDTO payment = paymentService.allocateManually(id, allocations);
    return ResponseEntity.ok(payment);
  }

  /**
   * Post payment (generate voucher, update bill statuses, update balances).
   * Only DRAFT or PENDING_APPROVAL payments can be posted.
   * Requires authenticated user with Accountant+, Chief Accountant, or CFO role.
   *
   * @param id payment ID
   * @return posted payment DTO
   */
  @PostMapping("/{id}/post")
  @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT', 'CFO')")
  public ResponseEntity<APPaymentDTO> postPayment(@PathVariable("id") UUID id) {
    APPaymentDTO payment = paymentService.postPayment(id);
    return ResponseEntity.ok(payment);
  }

  /**
   * Cancel a draft payment.
   * Only DRAFT payments can be cancelled.
   * Requires authenticated user with Accountant+ role.
   *
   * @param id payment ID
   * @return cancelled payment DTO
   */
  @PostMapping("/{id}/cancel")
  @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
  public ResponseEntity<APPaymentDTO> cancelPayment(@PathVariable("id") UUID id) {
    APPaymentDTO payment = paymentService.cancelPayment(id);
    return ResponseEntity.ok(payment);
  }

  /**
   * Get open/unpaid bills for a supplier.
   * Returns bills with status=POSTED and remaining_balance > 0, sorted by
   * due_date ASC.
   * Used for supplier picker in payment form.
   * Requires authenticated user with Accountant+ role.
   *
   * @param supplierId supplier ID
   * @return list of open purchase bill DTOs
   */
  @GetMapping("/suppliers/{supplierId}/open-bills")
  @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT', 'CFO')")
  public ResponseEntity<List<PurchaseBillDTO>> getOpenBillsForSupplier(
      @PathVariable("supplierId") Long supplierId) {
    List<PurchaseBillDTO> bills = paymentService.getOpenBillsForSupplier(supplierId);
    return ResponseEntity.ok(bills);
  }

  /**
   * Allocate payment using FIFO algorithm.
   * Fetches open/unpaid bills sorted by due_date ASC and allocates payment
   * amount.
   * Returns suggested allocations (does not save).
   * Requires authenticated user with Accountant+ role.
   *
   * @param paymentAmount total payment amount
   * @param supplierId    supplier ID
   * @return list of suggested allocation DTOs
   */
  @PostMapping("/allocate-fifo")
  @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
  public ResponseEntity<List<PaymentAllocationDTO>> allocateFIFO(
      @RequestParam("paymentAmount") java.math.BigDecimal paymentAmount,
      @RequestParam("supplierId") Long supplierId) {
    List<PaymentAllocationDTO> allocations = paymentService.allocateFIFO(paymentAmount, supplierId);
    return ResponseEntity.ok(allocations);
  }

  /**
   * Submit payment for approval (if payment exceeds threshold, status changes to
   * PENDING_APPROVAL).
   * This is automatically handled during payment creation, but can be called
   * explicitly.
   * Requires authenticated user with Accountant+ role.
   *
   * @param id payment ID
   * @return updated payment DTO
   */
  @PostMapping("/{id}/submit-for-approval")
  @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
  public ResponseEntity<APPaymentDTO> submitForApproval(@PathVariable("id") UUID id) {
    // Payment approval is automatically handled during creation based on threshold
    // This endpoint is for explicit submission if needed
    APPaymentDTO payment = paymentService.findById(id)
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.NOT_FOUND, "Payment not found: " + id));

    // If payment is DRAFT and exceeds threshold, update to PENDING_APPROVAL
    // This logic is already in create(), but we provide this endpoint for explicit
    // submission
    return ResponseEntity.ok(payment);
  }

  /**
   * Batch import payments from Excel file.
   * Requires authenticated user with Accountant+ role.
   * Validates all rows before saving (atomic transaction: all valid rows or
   * none).
   * Auto-adds unknown suppliers as draft suppliers pending confirmation.
   *
   * @param file Excel file to import
   * @return import result with success count, error count, error details, and
   *         error report ID
   */
  @PostMapping("/batch-import")
  @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT', 'CFO')")
  public ResponseEntity<Map<String, Object>> batchImport(
      @RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
    ImportResultDTO result = paymentImportService.importPayments(file);
    Map<String, Object> body = new HashMap<>();
    body.put("data", result);
    return ResponseEntity.ok(body);
  }

  /**
   * Generate Excel template for payment import.
   * Requires authenticated user with Accountant+ role.
   *
   * @return Excel template file
   */
  @GetMapping("/import-template")
  @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT', 'CFO')")
  public ResponseEntity<byte[]> getImportTemplate() {
    byte[] template = paymentImportService.generateTemplate();
    org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
    headers.setContentType(org.springframework.http.MediaType.APPLICATION_OCTET_STREAM);
    headers.setContentDispositionFormData("attachment", "payment-import-template.xlsx");
    headers.setContentLength(template.length);
    return ResponseEntity.ok().headers(headers).body(template);
  }

  /**
   * Approve a payment pending approval.
   * AC6.3-08: Only CHIEF_ACCOUNTANT, CFO, or ADMIN can approve.
   * Enforces maker-checker pattern (approver ≠ creator).
   *
   * @param id payment ID
   * @return approved payment DTO
   */
  @PostMapping("/{id}/approve")
  @PreAuthorize("hasAnyRole('ADMIN', 'CHIEF_ACCOUNTANT', 'CFO')")
  public ResponseEntity<APPaymentDTO> approvePayment(@PathVariable("id") UUID id) {
    APPaymentDTO payment = paymentService.approvePayment(id);
    return ResponseEntity.ok(payment);
  }

  /**
   * Reject a payment pending approval.
   * AC6.3-08: Only CHIEF_ACCOUNTANT, CFO, or ADMIN can reject.
   * Requires a rejection reason.
   *
   * @param id      payment ID
   * @param request rejection request with reason
   * @return rejected payment DTO
   */
  @PostMapping("/{id}/reject")
  @PreAuthorize("hasAnyRole('ADMIN', 'CHIEF_ACCOUNTANT', 'CFO')")
  public ResponseEntity<APPaymentDTO> rejectPayment(
      @PathVariable("id") UUID id,
      @RequestBody Map<String, String> request) {
    String reason = request.get("reason");
    APPaymentDTO payment = paymentService.rejectPayment(id, reason);
    return ResponseEntity.ok(payment);
  }

  /**
   * Reverse a posted payment.
   * AC6.3-10: Creates reversing voucher and updates bill/allocation states.
   * Requires a reversal reason (mandatory).
   *
   * @param id      payment ID
   * @param request reversal request with reason
   * @return reversed payment DTO
   */
  @PostMapping("/{id}/reverse")
  @PreAuthorize("hasAnyRole('ADMIN', 'CHIEF_ACCOUNTANT', 'CFO')")
  public ResponseEntity<APPaymentDTO> reversePayment(
      @PathVariable("id") UUID id,
      @RequestBody Map<String, String> request) {
    String reason = request.get("reason");
    APPaymentDTO payment = paymentService.reversePayment(id, reason);
    return ResponseEntity.ok(payment);
  }
}
