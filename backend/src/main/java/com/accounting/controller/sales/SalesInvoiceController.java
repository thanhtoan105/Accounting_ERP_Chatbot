package com.accounting.controller.sales;

import com.accounting.dto.ApprovalWorkflowDTO;
import com.accounting.dto.SalesInvoiceCreateRequest;
import com.accounting.dto.SalesInvoiceDTO;
import com.accounting.dto.SalesInvoiceListDTO;
import com.accounting.dto.SalesInvoiceValidationResult;
import com.accounting.entity.SalesInvoiceStatus;
// import com.accounting.service.SalesInvoiceImportService; // TODO: Implement in future story
import com.accounting.service.SalesInvoiceApprovalService;
import com.accounting.service.SalesInvoiceService;
import com.accounting.service.SalesInvoiceValidationService;
// import com.accounting.service.sales.SalesInvoiceAttachmentService; // TODO: Implement in future story
import jakarta.validation.Valid;
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
import jakarta.servlet.http.HttpServletRequest;
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

/**
 * REST controller for SalesInvoice operations.
 * All authenticated users with Accountant+ role can view sales invoices; edit
 * requires Accountant+ role.
 */
@RestController
@RequestMapping("/api/v1/ar/sales-invoices")
public class SalesInvoiceController {

  private final SalesInvoiceService salesInvoiceService;
  private final SalesInvoiceValidationService salesInvoiceValidationService;
  private final SalesInvoiceApprovalService salesInvoiceApprovalService;
  // private final SalesInvoiceImportService salesInvoiceImportService; // TODO:
  // Implement
  // private final SalesInvoiceAttachmentService salesInvoiceAttachmentService; //
  // TODO: Implement

  public SalesInvoiceController(
      SalesInvoiceService salesInvoiceService,
      SalesInvoiceValidationService salesInvoiceValidationService,
      SalesInvoiceApprovalService salesInvoiceApprovalService) {
    // SalesInvoiceImportService salesInvoiceImportService, // TODO: Implement
    // SalesInvoiceAttachmentService salesInvoiceAttachmentService) { // TODO:
    // Implement
    this.salesInvoiceService = salesInvoiceService;
    this.salesInvoiceValidationService = salesInvoiceValidationService;
    this.salesInvoiceApprovalService = salesInvoiceApprovalService;
    // this.salesInvoiceImportService = salesInvoiceImportService; // TODO:
    // Implement
    // this.salesInvoiceAttachmentService = salesInvoiceAttachmentService; // TODO:
    // Implement
  }

  /**
   * Get paginated, filtered, sorted sales invoice list.
   * Supports query params: page, size, customer, status, dateFrom, dateTo,
   * search, sort.
   * Requires authenticated user with Accountant+ role.
   *
   * @param page     page number (0-based, default: 0)
   * @param size     page size (default: 20, max: 100)
   * @param customer filter by customer ID (optional)
   * @param status   filter by status (optional: DRAFT, PENDING_APPROVAL, POSTED,
   *                 etc.)
   * @param dateFrom filter by date from (optional, format: YYYY-MM-DD)
   * @param dateTo   filter by date to (optional, format: YYYY-MM-DD)
   * @param search   search term for invoice number or reference (optional,
   *                 supports Vietnamese unaccented matching)
   * @param sort     sort parameters (optional, format: field,direction e.g.,
   *                 invoiceDate,desc or status,asc)
   * @return paginated sales invoice list
   */
  @GetMapping
  @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT', 'CFO')")
  public ResponseEntity<Map<String, Object>> getSalesInvoices(
      @RequestParam(required = false, defaultValue = "0") int page,
      @RequestParam(required = false, defaultValue = "20") int size,
      @RequestParam(required = false) Long customer,
      @RequestParam(required = false) SalesInvoiceStatus status,
      @RequestParam(required = false) LocalDate dateFrom,
      @RequestParam(required = false) LocalDate dateTo,
      @RequestParam(required = false) String search,
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
    Page<SalesInvoiceListDTO> invoices = salesInvoiceService.findAll(pageable, customer, status, dateFrom, dateTo,
        search);

    // Build response matching spec format: { data: { content:
    // SalesInvoiceListDTO[], totalElements: number, totalPages: number }, meta:
    // {...} }
    Map<String, Object> data = new HashMap<>();
    data.put("content", invoices.getContent());
    data.put("totalElements", invoices.getTotalElements());
    data.put("totalPages", invoices.getTotalPages());

    Map<String, Object> meta = new HashMap<>();
    meta.put("page", invoices.getNumber());
    meta.put("size", invoices.getSize());
    meta.put("totalElements", invoices.getTotalElements());
    meta.put("totalPages", invoices.getTotalPages());

    Map<String, Object> body = new HashMap<>();
    body.put("data", data);
    body.put("meta", meta);

    return ResponseEntity.ok(body);
  }

  /**
   * Get sales invoice by ID (with lines).
   * Requires authenticated user with Accountant+ role.
   *
   * @param invoiceId sales invoice ID
   * @return sales invoice details with lines array
   */
  @GetMapping("/{id}")
  @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT', 'CFO')")
  public ResponseEntity<Map<String, Object>> getSalesInvoiceById(@PathVariable("id") UUID invoiceId) {
    return salesInvoiceService
        .findById(invoiceId)
        .map(
            invoice -> {
              Map<String, Object> body = new HashMap<>();
              body.put("data", invoice);
              return ResponseEntity.ok(body);
            })
        .orElse(ResponseEntity.notFound().build());
  }

  /**
   * Create a new sales invoice with line items.
   * Requires authenticated user with Accountant+ role.
   *
   * @param request sales invoice create request with line items
   * @return created sales invoice DTO
   */
  @PostMapping
  @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT', 'CFO')")
  public ResponseEntity<Map<String, Object>> createSalesInvoice(
      @Valid @RequestBody SalesInvoiceCreateRequest request) {
    try {
      SalesInvoiceDTO invoice = salesInvoiceService.create(request);
      Map<String, Object> body = new HashMap<>();
      body.put("data", invoice);
      return ResponseEntity.status(HttpStatus.CREATED).body(body);
    } catch (ResponseStatusException e) {
      if (e.getStatusCode() == HttpStatus.BAD_REQUEST && e.getReason() != null
          && e.getReason().contains("Validation failed")) {
        // Parse validation errors from service and return structured format
        SalesInvoiceValidationResult validationResult = salesInvoiceValidationService.validate(request, null);
        Map<String, Object> body = new HashMap<>();
        Map<String, Object> error = new HashMap<>();
        error.put("code", "VALIDATION_ERROR");
        error.put("message", "Validation failed");

        Map<String, Object> details = new HashMap<>();
        if (validationResult.getHeaderErrors() != null && !validationResult.getHeaderErrors().isEmpty()) {
          details.put("headerErrors", validationResult.getHeaderErrors());
        }
        if (validationResult.getLineErrors() != null && !validationResult.getLineErrors().isEmpty()) {
          details.put("lineErrors", validationResult.getLineErrors());
        }
        error.put("details", details);
        body.put("error", error);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
      }
      throw e;
    }
  }

  /**
   * Update an existing draft sales invoice.
   * Only DRAFT invoices can be updated.
   * Requires authenticated user with Accountant+ role.
   *
   * @param invoiceId sales invoice ID
   * @param request   sales invoice update request with line items
   * @return updated sales invoice DTO
   */
  @PutMapping("/{id}")
  @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT', 'CFO')")
  public ResponseEntity<Map<String, Object>> updateSalesInvoice(
      @PathVariable("id") UUID invoiceId, @Valid @RequestBody SalesInvoiceCreateRequest request) {
    try {
      SalesInvoiceDTO invoice = salesInvoiceService.update(invoiceId, request);
      Map<String, Object> body = new HashMap<>();
      body.put("data", invoice);
      return ResponseEntity.ok(body);
    } catch (ResponseStatusException e) {
      // Handle conflict errors (e.g., trying to update posted invoice)
      if (e.getStatusCode() == HttpStatus.CONFLICT) {
        Map<String, Object> errorBody = new HashMap<>();
        errorBody.put("error", e.getReason());
        errorBody.put("code", "CONFLICT");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorBody);
      }
      throw e;
    }
  }

  /**
   * Validate a sales invoice (without saving).
   * Returns detailed validation errors for client-side display.
   * For new invoices, use a placeholder UUID (e.g., all zeros).
   * Requires authenticated user with Accountant+ role.
   *
   * @param invoiceId sales invoice ID (use placeholder UUID for new invoices)
   * @param request   sales invoice request to validate
   * @return validation result with errors (if any)
   */
  @PostMapping("/{id}/validate")
  @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT', 'CFO')")
  public ResponseEntity<Map<String, Object>> validateSalesInvoice(
      @PathVariable("id") UUID invoiceId, @Valid @RequestBody SalesInvoiceCreateRequest request) {
    SalesInvoiceValidationResult result = salesInvoiceValidationService.validate(request, invoiceId);
    Map<String, Object> body = new HashMap<>();
    body.put("valid", result.isValid());
    body.put("headerErrors", result.getHeaderErrors());
    body.put("lineErrors", result.getLineErrors());
    return ResponseEntity.ok(body);
  }

  /**
   * Delete sales invoice with validation (only DRAFT status, creator-only unless
   * admin).
   * Requires deletion reason.
   * Requires authenticated user with Accountant+ role.
   *
   * @param invoiceId sales invoice ID
   * @param reason    deletion reason (required)
   * @return success response
   */
  @DeleteMapping("/{id}")
  @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT', 'CFO')")
  public ResponseEntity<Void> deleteSalesInvoice(
      @PathVariable("id") UUID invoiceId,
      @RequestParam(required = false) String reason,
      HttpServletRequest request) {

    // Validate reason is provided
    if (reason == null || reason.isBlank()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Deletion reason is required");
    }

    // Delete sales invoice (pass request for audit logging)
    salesInvoiceService.delete(invoiceId, reason, request);

    return ResponseEntity.noContent().build();
  }

  /**
   * Save draft sales invoice (autosave endpoint).
   * Requires authenticated user with Accountant+ role.
   *
   * @param invoiceId sales invoice ID (optional for new drafts)
   * @param request   sales invoice create/update request
   * @return saved draft sales invoice DTO
   */
  @PostMapping("/{id}/save-draft")
  @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT', 'CFO')")
  public ResponseEntity<Map<String, Object>> saveDraft(
      @PathVariable("id") UUID invoiceId, @Valid @RequestBody SalesInvoiceCreateRequest request) {
    request.setId(invoiceId);
    SalesInvoiceDTO invoice = salesInvoiceService.saveDraft(request);
    Map<String, Object> body = new HashMap<>();
    body.put("data", invoice);
    return ResponseEntity.ok(body);
  }

  /**
   * Get list of recoverable drafts for current user/admin.
   * Requires authenticated user with Accountant+ role.
   *
   * @return list of draft sales invoices
   */
  @GetMapping("/drafts")
  @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT', 'CFO')")
  public ResponseEntity<Map<String, Object>> getDrafts() {
    List<SalesInvoiceDTO> drafts = salesInvoiceService.getDrafts();
    Map<String, Object> body = new HashMap<>();
    body.put("data", drafts);
    body.put("count", drafts.size());
    return ResponseEntity.ok(body);
  }

  /**
   * Recover a draft sales invoice.
   * Requires authenticated user with Accountant+ role (creator or admin).
   *
   * @param invoiceId sales invoice ID
   * @return recovered sales invoice DTO
   */
  @PostMapping("/{id}/recover")
  @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT', 'CFO')")
  public ResponseEntity<Map<String, Object>> recoverDraft(@PathVariable("id") UUID invoiceId) {
    SalesInvoiceDTO invoice = salesInvoiceService.recoverDraft(invoiceId);
    Map<String, Object> body = new HashMap<>();
    body.put("data", invoice);
    return ResponseEntity.ok(body);
  }

  // TODO: Implement batch import when SalesInvoiceImportService is ready
  /*
   * @PostMapping("/batch-import")
   * 
   * @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT', 'CFO')")
   * public ResponseEntity<Map<String, Object>> batchImport(
   * 
   * @RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
   * ImportResultDTO result = salesInvoiceImportService.importInvoices(file);
   * Map<String, Object> body = new HashMap<>();
   * body.put("data", result);
   * return ResponseEntity.ok(body);
   * }
   * 
   * @GetMapping("/import-template")
   * 
   * @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT', 'CFO')")
   * public ResponseEntity<byte[]> getImportTemplate() {
   * byte[] template = salesInvoiceImportService.generateTemplate();
   * org.springframework.http.HttpHeaders headers = new
   * org.springframework.http.HttpHeaders();
   * headers.setContentType(org.springframework.http.MediaType.
   * APPLICATION_OCTET_STREAM);
   * headers.setContentDispositionFormData("attachment",
   * "sales-invoice-import-template.xlsx");
   * headers.setContentLength(template.length);
   * return ResponseEntity.ok().headers(headers).body(template);
   * }
   */

  /**
   * Create a credit note (negative invoice) that references an original invoice.
   * Credit notes have inverted GL splits and are automatically posted.
   * Requires authenticated user with Accountant+ role.
   *
   * @param originalInvoiceId ID of the original invoice (must be POSTED)
   * @param request          credit note create request with line items
   * @return created credit note DTO
   */
  @PostMapping("/{id}/credit-note")
  @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT', 'CFO')")
  public ResponseEntity<Map<String, Object>> createCreditNote(
      @PathVariable("id") UUID originalInvoiceId,
      @Valid @RequestBody SalesInvoiceCreateRequest request) {
    try {
      SalesInvoiceDTO creditNote = salesInvoiceService.createCreditNote(originalInvoiceId, request);
      Map<String, Object> body = new HashMap<>();
      body.put("data", creditNote);
      return ResponseEntity.status(HttpStatus.CREATED).body(body);
    } catch (ResponseStatusException e) {
      if (e.getStatusCode() == HttpStatus.BAD_REQUEST) {
        Map<String, Object> errorBody = new HashMap<>();
        errorBody.put("error", e.getReason());
        errorBody.put("code", "VALIDATION_ERROR");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorBody);
      }
      throw e;
    }
  }

  // TODO: Implement attachment endpoints when SalesInvoiceAttachmentService is
  // ready
  /*
   * @PostMapping("/{id}/attachments")
   * 
   * @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT', 'CFO')")
   * public ResponseEntity<Map<String, Object>> uploadAttachment(
   * 
   * @PathVariable("id") UUID invoiceId,
   * 
   * @RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
   * try {
   * SalesInvoiceAttachmentDTO attachment =
   * salesInvoiceAttachmentService.uploadAttachment(invoiceId, file);
   * Map<String, Object> body = new HashMap<>();
   * body.put("data", attachment);
   * body.put("message", "Attachment uploaded successfully");
   * return ResponseEntity.status(HttpStatus.CREATED).body(body);
   * } catch (ResponseStatusException e) {
   * throw e;
   * }
   * }
   * 
   * @GetMapping("/{id}/attachments")
   * 
   * @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT', 'CFO')")
   * public ResponseEntity<Map<String, Object>> listAttachments(
   * 
   * @PathVariable("id") UUID invoiceId) {
   * List<SalesInvoiceAttachmentDTO> attachments =
   * salesInvoiceAttachmentService.listAttachments(invoiceId);
   * Map<String, Object> body = new HashMap<>();
   * body.put("data", attachments);
   * body.put("count", attachments.size());
   * return ResponseEntity.ok(body);
   * }
   * 
   * @GetMapping("/{id}/attachments/{attachmentId}/download")
   * 
   * @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT', 'CFO')")
   * public ResponseEntity<Void> downloadAttachment(
   * 
   * @PathVariable("id") UUID invoiceId,
   * 
   * @PathVariable UUID attachmentId) {
   * String signedUrl = salesInvoiceAttachmentService.generateSignedUrl(invoiceId,
   * attachmentId);
   * return ResponseEntity.status(HttpStatus.FOUND)
   * .header(org.springframework.http.HttpHeaders.LOCATION, signedUrl)
   * .build();
   * }
   * 
   * @GetMapping("/{id}/attachments/{attachmentId}/preview")
   * 
   * @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT', 'CFO')")
   * public ResponseEntity<Void> previewAttachment(
   * 
   * @PathVariable("id") UUID invoiceId,
   * 
   * @PathVariable UUID attachmentId) {
   * String signedUrl = salesInvoiceAttachmentService.generateSignedUrl(invoiceId,
   * attachmentId);
   * return ResponseEntity.status(HttpStatus.FOUND)
   * .header(org.springframework.http.HttpHeaders.LOCATION, signedUrl)
   * .build();
   * }
   * 
   * @DeleteMapping("/{id}/attachments/{attachmentId}")
   * 
   * @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT', 'CFO')")
   * public ResponseEntity<Void> deleteAttachment(
   * 
   * @PathVariable("id") UUID invoiceId,
   * 
   * @PathVariable UUID attachmentId,
   * 
   * @RequestBody(required = false) Map<String, String> request) {
   * String reason = request != null ? request.get("reason") : null;
   * if (reason == null || reason.trim().isEmpty()) {
   * throw new ResponseStatusException(
   * HttpStatus.BAD_REQUEST, "Deletion reason is required");
   * }
   * salesInvoiceAttachmentService.deleteAttachment(invoiceId, attachmentId,
   * reason);
   * return ResponseEntity.noContent().build();
   * }
   */

  /**
   * Submit sales invoice for approval.
   * Requires Accountant+ role.
   *
   * @param id sales invoice ID
   * @return approval workflow DTO
   */
  @PostMapping("/{id}/submit-for-approval")
  @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT', 'CFO')")
  public ResponseEntity<ApprovalWorkflowDTO> submitForApproval(@PathVariable UUID id) {
    Long submitterId = com.accounting.security.SecurityUtils.getCurrentUserId();
    ApprovalWorkflowDTO workflow = salesInvoiceApprovalService.submitForApproval(id, submitterId);
    return ResponseEntity.ok(workflow);
  }

  /**
   * Approve a pending sales invoice.
   * Requires Chief Accountant or CFO role.
   *
   * @param workflowId approval workflow ID
   * @param request    approval request with optional reason
   * @return updated approval workflow DTO
   */
  @PostMapping("/approvals/{workflowId}/approve")
  @PreAuthorize("hasAnyRole('ADMIN', 'CHIEF_ACCOUNTANT', 'CFO')")
  public ResponseEntity<ApprovalWorkflowDTO> approve(
      @PathVariable UUID workflowId,
      @RequestBody(required = false) Map<String, String> request) {
    Long approverId = com.accounting.security.SecurityUtils.getCurrentUserId();
    String reason = request != null ? request.get("reason") : null;
    ApprovalWorkflowDTO workflow = salesInvoiceApprovalService.approve(workflowId, approverId, reason);
    return ResponseEntity.ok(workflow);
  }

  /**
   * Reject a pending sales invoice.
   * Requires Chief Accountant or CFO role.
   *
   * @param workflowId approval workflow ID
   * @param request    rejection request with mandatory reason
   * @return updated approval workflow DTO
   */
  @PostMapping("/approvals/{workflowId}/reject")
  @PreAuthorize("hasAnyRole('ADMIN', 'CHIEF_ACCOUNTANT', 'CFO')")
  public ResponseEntity<ApprovalWorkflowDTO> reject(
      @PathVariable UUID workflowId,
      @RequestBody Map<String, String> request) {
    Long approverId = com.accounting.security.SecurityUtils.getCurrentUserId();
    String reason = request != null ? request.get("reason") : null;
    if (reason == null || reason.trim().isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rejection reason is mandatory");
    }
    ApprovalWorkflowDTO workflow = salesInvoiceApprovalService.reject(workflowId, approverId, reason);
    return ResponseEntity.ok(workflow);
  }

  /**
   * Get all pending approval workflows for sales invoices.
   * Requires Chief Accountant or CFO role.
   *
   * @return list of pending approval workflows
   */
  @GetMapping("/approvals/pending")
  @PreAuthorize("hasAnyRole('ADMIN', 'CHIEF_ACCOUNTANT', 'CFO')")
  public ResponseEntity<List<ApprovalWorkflowDTO>> getPendingApprovals() {
    List<ApprovalWorkflowDTO> workflows = salesInvoiceApprovalService.getPendingApprovals();
    return ResponseEntity.ok(workflows);
  }

  /**
   * Get approval history for a specific sales invoice.
   * Requires Accountant+ role.
   *
   * @param id sales invoice ID
   * @return list of approval workflows for the invoice
   */
  @GetMapping("/{id}/approval-history")
  @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT', 'CFO')")
  public ResponseEntity<List<ApprovalWorkflowDTO>> getApprovalHistory(@PathVariable UUID id) {
    List<ApprovalWorkflowDTO> workflows = salesInvoiceApprovalService.getApprovalHistory(id);
    return ResponseEntity.ok(workflows);
  }

  /**
   * Get count of pending approvals for sales invoices.
   * Requires Chief Accountant or CFO role.
   *
   * @return count of pending approvals
   */
  @GetMapping("/approvals/pending/count")
  @PreAuthorize("hasAnyRole('ADMIN', 'CHIEF_ACCOUNTANT', 'CFO')")
  public ResponseEntity<Map<String, Long>> getPendingApprovalsCount() {
    long count = salesInvoiceApprovalService.getPendingApprovalsCount();
    return ResponseEntity.ok(Map.of("count", count));
  }
}
