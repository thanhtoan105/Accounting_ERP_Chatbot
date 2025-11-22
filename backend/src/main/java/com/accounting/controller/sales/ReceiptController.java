package com.accounting.controller.sales;

import com.accounting.dto.ARPaymentCreateRequest;
import com.accounting.dto.ARPaymentDTO;
import com.accounting.dto.ARPaymentListDTO;
import com.accounting.dto.ImportResultDTO;
import com.accounting.dto.ReceiptAllocationRequest;
import com.accounting.entity.ReceiptStatus;
import com.accounting.service.ReceiptImportService;
import com.accounting.service.ReceiptService;
import com.accounting.service.ReceiptValidationService;
import jakarta.validation.Valid;
import java.time.LocalDate;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * REST controller for AR receipt (customer payment) operations.
 * All authenticated users with Accountant+ role can view receipts;
 * create/edit requires Accountant+ role; post/reverse requires Chief
 * Accountant+ role.
 */
@RestController
@RequestMapping("/api/v1/ar/receipts")
public class ReceiptController {

  private final ReceiptService receiptService;
  private final ReceiptValidationService receiptValidationService;
  private final ReceiptImportService receiptImportService;

  public ReceiptController(
      ReceiptService receiptService,
      ReceiptValidationService receiptValidationService,
      ReceiptImportService receiptImportService) {
    this.receiptService = receiptService;
    this.receiptValidationService = receiptValidationService;
    this.receiptImportService = receiptImportService;
  }

  /**
   * Get paginated, filtered, sorted receipt list.
   * Supports query params: page, size, customer, status, dateFrom, dateTo,
   * search, standalone, sort.
   * Requires authenticated user with Accountant+ role.
   *
   * @param page       page number (0-based, default: 0)
   * @param size       page size (default: 20, max: 100)
   * @param customer   filter by customer ID (optional)
   * @param status     filter by status (optional: DRAFT, POSTED, REVERSED)
   * @param dateFrom   filter by date from (optional, format: YYYY-MM-DD)
   * @param dateTo     filter by date to (optional, format: YYYY-MM-DD)
   * @param search     search term for receipt number, reference, or payee
   *                   (optional, supports Vietnamese unaccented matching)
   * @param standalone filter by standalone flag (optional)
   * @param sort       sort parameters (optional, format: field,direction e.g.,
   *                   receiptDate,desc)
   * @return paginated receipt list
   */
  @GetMapping
  @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT', 'CFO')")
  public ResponseEntity<Map<String, Object>> getReceipts(
      @RequestParam(required = false, defaultValue = "0") int page,
      @RequestParam(required = false, defaultValue = "20") int size,
      @RequestParam(required = false) Long customer,
      @RequestParam(required = false) ReceiptStatus status,
      @RequestParam(required = false) LocalDate dateFrom,
      @RequestParam(required = false) LocalDate dateTo,
      @RequestParam(required = false) String search,
      @RequestParam(required = false) Boolean standalone,
      @RequestParam(required = false, defaultValue = "receiptDate,desc") String sort) {

    // Validate page size
    if (size > 100) {
      size = 100;
    }

    // Parse sort parameter
    Sort sortObj = Sort.by(Sort.Direction.DESC, "receiptDate");
    if (sort != null && !sort.isBlank()) {
      String[] sortParts = sort.split(",");
      if (sortParts.length == 2) {
        Sort.Direction direction = sortParts[1].equalsIgnoreCase("asc") ? Sort.Direction.ASC : Sort.Direction.DESC;
        sortObj = Sort.by(direction, sortParts[0]);
      }
    }

    Pageable pageable = PageRequest.of(page, size, sortObj);

    // Build filters map
    Map<String, Object> filters = new HashMap<>();
    if (customer != null) {
      filters.put("customerId", customer);
    }
    if (status != null) {
      filters.put("status", status);
    }
    if (dateFrom != null) {
      filters.put("dateFrom", dateFrom);
    }
    if (dateTo != null) {
      filters.put("dateTo", dateTo);
    }
    if (search != null && !search.isBlank()) {
      filters.put("search", search.trim());
    }
    if (standalone != null) {
      filters.put("standalone", standalone);
    }

    Page<ARPaymentListDTO> receipts = receiptService.findAll(pageable, filters);

    // Build response
    Map<String, Object> response = new HashMap<>();
    response.put("receipts", receipts.getContent());
    response.put("currentPage", receipts.getNumber());
    response.put("totalItems", receipts.getTotalElements());
    response.put("totalPages", receipts.getTotalPages());

    return ResponseEntity.ok(response);
  }

  /**
   * Get receipt by ID with full details including allocations.
   * Requires authenticated user with Accountant+ role.
   *
   * @param id receipt ID
   * @return receipt details
   */
  @GetMapping("/{id}")
  @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT', 'CFO')")
  public ResponseEntity<ARPaymentDTO> getReceiptById(@PathVariable UUID id) {
    ARPaymentDTO receipt = receiptService.findById(id);
    return ResponseEntity.ok(receipt);
  }

  /**
   * Create new receipt.
   * Requires authenticated user with Accountant+ role.
   * Standalone receipts require ADMIN role.
   *
   * @param request receipt creation request
   * @return created receipt
   */
  @PostMapping
  @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT')")
  public ResponseEntity<ARPaymentDTO> createReceipt(
      @Valid @RequestBody ARPaymentCreateRequest request) {

    // Validate standalone receipts require admin role
    if (request.getIsStandalone() != null && request.getIsStandalone()) {
      if (!com.accounting.security.SecurityUtils.hasRole("ADMIN")) {
        throw new ResponseStatusException(
            HttpStatus.FORBIDDEN, "Standalone receipts require ADMIN role");
      }
    }

    ARPaymentDTO receipt = receiptService.create(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(receipt);
  }

  /**
   * Update existing receipt (DRAFT only).
   * Requires authenticated user with Accountant+ role.
   *
   * @param id      receipt ID
   * @param request receipt update request
   * @return updated receipt
   */
  @PutMapping("/{id}")
  @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT')")
  public ResponseEntity<ARPaymentDTO> updateReceipt(
      @PathVariable UUID id, @Valid @RequestBody ARPaymentCreateRequest request) {
    ARPaymentDTO receipt = receiptService.update(id, request);
    return ResponseEntity.ok(receipt);
  }

  /**
   * Delete receipt (DRAFT only).
   * Requires authenticated user with Chief Accountant+ role.
   *
   * @param id receipt ID
   * @return no content
   */
  @DeleteMapping("/{id}")
  @PreAuthorize("hasAnyRole('ADMIN', 'CHIEF_ACCOUNTANT')")
  public ResponseEntity<Void> deleteReceipt(@PathVariable UUID id) {
    receiptService.delete(id);
    return ResponseEntity.noContent().build();
  }

  /**
   * Allocate receipt to invoices (DRAFT only).
   * Requires authenticated user with Accountant+ role.
   *
   * @param id          receipt ID
   * @param allocations list of allocation requests
   * @return updated receipt with allocations
   */
  @PostMapping("/{id}/allocate")
  @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT')")
  public ResponseEntity<ARPaymentDTO> allocateInvoices(
      @PathVariable UUID id, @Valid @RequestBody List<ReceiptAllocationRequest> allocations) {
    ARPaymentDTO receipt = receiptService.allocateInvoices(id, allocations);
    return ResponseEntity.ok(receipt);
  }

  /**
   * Post receipt (generate GL voucher, update invoice statuses).
   * Requires authenticated user with Accountant+ role.
   *
   * @param id receipt ID
   * @return posted receipt
   */
  @PostMapping("/{id}/post")
  @PreAuthorize("hasAnyRole('ADMIN', 'CHIEF_ACCOUNTANT', 'ACCOUNTANT')")
  public ResponseEntity<ARPaymentDTO> postReceipt(@PathVariable UUID id) {
    ARPaymentDTO receipt = receiptService.postReceipt(id);
    return ResponseEntity.ok(receipt);
  }

  /**
   * Reverse posted receipt (create linked reversal voucher).
   * Requires authenticated user with Chief Accountant+ or CFO role.
   *
   * @param id   receipt ID
   * @param body request body containing reversal reason
   * @return reversed receipt
   */
  @PostMapping("/{id}/reverse")
  @PreAuthorize("hasAnyRole('ADMIN', 'CHIEF_ACCOUNTANT', 'CFO')")
  public ResponseEntity<ARPaymentDTO> reverseReceipt(
      @PathVariable UUID id, @RequestBody Map<String, String> body) {
    String reason = body.get("reason");
    if (reason == null || reason.isBlank()) {
      return ResponseEntity.badRequest().build();
    }

    ARPaymentDTO receipt = receiptService.reverseReceipt(id, reason);
    return ResponseEntity.ok(receipt);
  }

  /**
   * Get open invoices for a customer (for allocation picker).
   * Requires authenticated user with Accountant+ role.
   *
   * @param customerId customer ID
   * @return list of open invoices with remaining balances
   */
  @GetMapping("/open-invoices/{customerId}")
  @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT', 'CFO')")
  public ResponseEntity<List<Map<String, Object>>> getOpenInvoices(
      @PathVariable Long customerId) {
    List<Map<String, Object>> openInvoices = receiptService.getOpenInvoicesForCustomer(customerId);
    return ResponseEntity.ok(openInvoices);
  }

  /**
   * Validate receipt data before submission.
   * Requires authenticated user with Accountant+ role.
   *
   * @param request receipt validation request
   * @return validation result
   */
  @PostMapping("/validate")
  @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT')")
  public ResponseEntity<Map<String, Object>> validateReceipt(
      @Valid @RequestBody ARPaymentCreateRequest request) {

    // Perform validation checks
    var validationResult = receiptValidationService.validateAllocations(
        request.getAllocations(), request.getAmount());

    Map<String, Object> response = new HashMap<>();
    response.put("valid", validationResult.isValid());
    response.put("errors", validationResult.getFieldErrors());
    response.put("warnings", validationResult.getGlobalErrors());

    return ResponseEntity.ok(response);
  }

  /**
   * Generate receipt number for a given date (preview).
   * Requires authenticated user with Accountant+ role.
   *
   * @param date receipt date (format: YYYY-MM-DD)
   * @return generated receipt number
   */
  @GetMapping("/generate-number")
  @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT')")
  public ResponseEntity<Map<String, String>> generateReceiptNumber(
      @RequestParam LocalDate date) {
    String receiptNumber = receiptService.generateReceiptNumber(date);
    Map<String, String> response = new HashMap<>();
    response.put("receiptNumber", receiptNumber);
    return ResponseEntity.ok(response);
  }

  /**
   * Import receipts from Excel file (batch import).
   * Validates all rows before saving (atomic transaction: all valid rows or
   * none).
   * Requires authenticated user with Accountant+ role.
   *
   * @param file Excel file to import (.xlsx or .xls)
   * @return import result with success count, error count, and error details
   */
  @PostMapping("/batch-import")
  @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT')")
  public ResponseEntity<ImportResultDTO> batchImport(@RequestParam("file") MultipartFile file) {
    if (file.isEmpty()) {
      return ResponseEntity.badRequest().build();
    }
    ImportResultDTO result = receiptImportService.importReceipts(file);
    return ResponseEntity.ok(result);
  }

  /**
   * Download Excel template for receipt batch import.
   * Returns an Excel file with headers and example data.
   *
   * @return Excel template file
   */
  @GetMapping("/import-template")
  @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT')")
  public ResponseEntity<byte[]> downloadImportTemplate() {
    byte[] template = receiptImportService.generateTemplate();
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
    headers.setContentDispositionFormData("attachment", "receipt-import-template.xlsx");
    return ResponseEntity.ok().headers(headers).body(template);
  }
}
