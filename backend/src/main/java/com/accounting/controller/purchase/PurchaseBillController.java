package com.accounting.controller.purchase;

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

import com.accounting.dto.ImportResultDTO;
import com.accounting.dto.PurchaseBillAttachmentDTO;
import com.accounting.dto.PurchaseBillCreateRequest;
import com.accounting.dto.PurchaseBillDTO;
import com.accounting.dto.PurchaseBillListDTO;
import com.accounting.dto.PurchaseBillValidationResult;
import com.accounting.entity.PurchaseBillStatus;
import com.accounting.service.PurchaseBillImportService;
import com.accounting.service.PurchaseBillService;
import com.accounting.service.PurchaseBillValidationService;
import com.accounting.service.purchase.PurchaseBillAttachmentService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/**
 * REST controller for PurchaseBill operations.
 * All authenticated users with Accountant+ role can view purchase bills; edit requires Accountant+ role.
 */
@RestController
@RequestMapping("/api/v1/purchase-bills")
public class PurchaseBillController {

  private final PurchaseBillService purchaseBillService;
  private final PurchaseBillValidationService purchaseBillValidationService;
  private final PurchaseBillImportService purchaseBillImportService;
  private final PurchaseBillAttachmentService purchaseBillAttachmentService;

  public PurchaseBillController(
      PurchaseBillService purchaseBillService,
      PurchaseBillValidationService purchaseBillValidationService,
      PurchaseBillImportService purchaseBillImportService,
      PurchaseBillAttachmentService purchaseBillAttachmentService) {
    this.purchaseBillService = purchaseBillService;
    this.purchaseBillValidationService = purchaseBillValidationService;
    this.purchaseBillImportService = purchaseBillImportService;
    this.purchaseBillAttachmentService = purchaseBillAttachmentService;
  }

  /**
   * Get paginated, filtered, sorted purchase bill list.
   * Supports query params: page, size, supplier, status, dateFrom, dateTo, search, sort.
   * Requires authenticated user with Accountant+ role.
   *
   * @param page     page number (0-based, default: 0)
   * @param size     page size (default: 20, max: 100)
   * @param supplier filter by supplier ID (optional)
   * @param status   filter by status (optional: DRAFT, PENDING_APPROVAL, POSTED, etc.)
   * @param dateFrom filter by date from (optional, format: YYYY-MM-DD)
   * @param dateTo   filter by date to (optional, format: YYYY-MM-DD)
   * @param search   search term for bill number or reference (optional, supports Vietnamese unaccented matching)
   * @param sort     sort parameters (optional, format: field,direction e.g., billDate,desc or status,asc)
   * @return paginated purchase bill list
   */
  @GetMapping
  @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT', 'CFO')")
  public ResponseEntity<Map<String, Object>> getPurchaseBills(
      @RequestParam(required = false, defaultValue = "0") int page,
      @RequestParam(required = false, defaultValue = "20") int size,
      @RequestParam(required = false) Long supplier,
      @RequestParam(required = false) PurchaseBillStatus status,
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
          Sort.Direction direction =
              "desc".equalsIgnoreCase(parts[1].trim()) ? Sort.Direction.DESC : Sort.Direction.ASC;
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
    Page<PurchaseBillListDTO> bills =
        purchaseBillService.findAll(pageable, supplier, status, dateFrom, dateTo, search);

    // Build response matching spec format: { data: { content: PurchaseBillListDTO[], totalElements: number, totalPages: number }, meta: {...} }
    Map<String, Object> data = new HashMap<>();
    data.put("content", bills.getContent());
    data.put("totalElements", bills.getTotalElements());
    data.put("totalPages", bills.getTotalPages());

    Map<String, Object> meta = new HashMap<>();
    meta.put("page", bills.getNumber());
    meta.put("size", bills.getSize());
    meta.put("totalElements", bills.getTotalElements());
    meta.put("totalPages", bills.getTotalPages());

    Map<String, Object> body = new HashMap<>();
    body.put("data", data);
    body.put("meta", meta);

    return ResponseEntity.ok(body);
  }

  /**
   * Get purchase bill by ID (with lines).
   * Requires authenticated user with Accountant+ role.
   *
   * @param billId purchase bill ID
   * @return purchase bill details with lines array
   */
  @GetMapping("/{id}")
  @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT', 'CFO')")
  public ResponseEntity<Map<String, Object>> getPurchaseBillById(@PathVariable("id") UUID billId) {
    return purchaseBillService
        .findById(billId)
        .map(
            bill -> {
              Map<String, Object> body = new HashMap<>();
              body.put("data", bill);
              return ResponseEntity.ok(body);
            })
        .orElse(ResponseEntity.notFound().build());
  }

  /**
   * Create a new purchase bill with line items.
   * Requires authenticated user with Accountant+ role.
   *
   * @param request purchase bill create request with line items
   * @return created purchase bill DTO
   */
  @PostMapping
  @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT', 'CFO')")
  public ResponseEntity<Map<String, Object>> createPurchaseBill(
      @Valid @RequestBody PurchaseBillCreateRequest request) {
    try {
      PurchaseBillDTO bill = purchaseBillService.create(request);
      Map<String, Object> body = new HashMap<>();
      body.put("data", bill);
      return ResponseEntity.status(HttpStatus.CREATED).body(body);
    } catch (ResponseStatusException e) {
      if (e.getStatusCode() == HttpStatus.BAD_REQUEST && e.getReason() != null && e.getReason().contains("Validation failed")) {
        // Parse validation errors from service and return structured format
        PurchaseBillValidationResult validationResult = purchaseBillValidationService.validate(request, null);
        Map<String, Object> body = new HashMap<>();
        Map<String, Object> error = new HashMap<>();
        error.put("code", "VALIDATION_ERROR");
        error.put("message", "Validation failed");
        
        Map<String, Object> details = new HashMap<>();
        if (!validationResult.getHeaderErrors().isEmpty()) {
          Map<String, List<String>> headerErrors = new HashMap<>();
          validationResult.getHeaderErrors().forEach((field, errors) -> {
            headerErrors.put(field, new ArrayList<>(errors));
          });
          details.put("headerErrors", headerErrors);
        }
        if (!validationResult.getLineErrors().isEmpty()) {
          Map<String, Map<String, List<String>>> lineErrors = new HashMap<>();
          validationResult.getLineErrors().forEach((lineNum, lineErrorsMap) -> {
            Map<String, List<String>> lineErrorMap = new HashMap<>();
            lineErrorsMap.forEach((field, errors) -> {
              lineErrorMap.put(field, new ArrayList<>(errors));
            });
            lineErrors.put(String.valueOf(lineNum), lineErrorMap);
          });
          details.put("lineErrors", lineErrors);
        }
        error.put("details", details);
        body.put("error", error);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
      }
      throw e;
    }
  }

  /**
   * Update an existing draft purchase bill.
   * Only DRAFT bills can be updated.
   * Requires authenticated user with Accountant+ role.
   *
   * @param billId  purchase bill ID
   * @param request purchase bill update request with line items
   * @return updated purchase bill DTO
   */
  @PutMapping("/{id}")
  @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT', 'CFO')")
  public ResponseEntity<Map<String, Object>> updatePurchaseBill(
      @PathVariable("id") UUID billId, @Valid @RequestBody PurchaseBillCreateRequest request) {
    try {
      PurchaseBillDTO bill = purchaseBillService.update(billId, request);
      Map<String, Object> body = new HashMap<>();
      body.put("data", bill);
      return ResponseEntity.ok(body);
    } catch (ResponseStatusException e) {
      // Handle conflict errors (e.g., trying to update posted bill)
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
   * Validate a purchase bill (without saving).
   * Returns detailed validation errors for client-side display.
   * For new bills, use a placeholder UUID (e.g., all zeros).
   * Requires authenticated user with Accountant+ role.
   *
   * @param billId  purchase bill ID (use placeholder UUID for new bills)
   * @param request purchase bill request to validate
   * @return validation result with errors (if any)
   */
  @PostMapping("/{id}/validate")
  @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT', 'CFO')")
  public ResponseEntity<Map<String, Object>> validatePurchaseBill(
      @PathVariable("id") UUID billId, @Valid @RequestBody PurchaseBillCreateRequest request) {
    PurchaseBillValidationResult result =
        purchaseBillValidationService.validate(request, billId);
    Map<String, Object> body = new HashMap<>();
    body.put("valid", result.isValid());
    body.put("headerErrors", result.getHeaderErrors());
    body.put("lineErrors", result.getLineErrors());
    return ResponseEntity.ok(body);
  }

  /**
   * Delete purchase bill with validation (only DRAFT status, creator-only unless admin).
   * Requires deletion reason.
   * Requires authenticated user with Accountant+ role.
   *
   * @param billId purchase bill ID
   * @param reason deletion reason (required)
   * @return success response
   */
  @DeleteMapping("/{id}")
  @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT', 'CFO')")
  public ResponseEntity<Void> deletePurchaseBill(
      @PathVariable("id") UUID billId,
      @RequestParam(required = false) String reason,
      HttpServletRequest request) {

    // Validate reason is provided
    if (reason == null || reason.isBlank()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Deletion reason is required");
    }

    // Delete purchase bill (pass request for audit logging)
    purchaseBillService.delete(billId, reason, request);

    return ResponseEntity.noContent().build();
  }

  /**
   * Save draft purchase bill (autosave endpoint).
   * Requires authenticated user with Accountant+ role.
   *
   * @param billId  purchase bill ID (optional for new drafts)
   * @param request purchase bill create/update request
   * @return saved draft purchase bill DTO
   */
  @PostMapping("/{id}/save-draft")
  @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT', 'CFO')")
  public ResponseEntity<Map<String, Object>> saveDraft(
      @PathVariable("id") UUID billId, @Valid @RequestBody PurchaseBillCreateRequest request) {
    request.setId(billId);
    PurchaseBillDTO bill = purchaseBillService.saveDraft(request);
    Map<String, Object> body = new HashMap<>();
    body.put("data", bill);
    return ResponseEntity.ok(body);
  }

  /**
   * Get list of recoverable drafts for current user/admin.
   * Requires authenticated user with Accountant+ role.
   *
   * @return list of draft purchase bills
   */
  @GetMapping("/drafts")
  @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT', 'CFO')")
  public ResponseEntity<Map<String, Object>> getDrafts() {
    List<PurchaseBillDTO> drafts = purchaseBillService.getDrafts();
    Map<String, Object> body = new HashMap<>();
    body.put("data", drafts);
    body.put("count", drafts.size());
    return ResponseEntity.ok(body);
  }

  /**
   * Recover a draft purchase bill.
   * Requires authenticated user with Accountant+ role (creator or admin).
   *
   * @param billId purchase bill ID
   * @return recovered purchase bill DTO
   */
  @PostMapping("/{id}/recover")
  @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT', 'CFO')")
  public ResponseEntity<Map<String, Object>> recoverDraft(@PathVariable("id") UUID billId) {
    PurchaseBillDTO bill = purchaseBillService.recoverDraft(billId);
    Map<String, Object> body = new HashMap<>();
    body.put("data", bill);
    return ResponseEntity.ok(body);
  }

  /**
   * Batch import purchase bills from Excel file.
   * Requires authenticated user with Accountant+ role.
   * Validates all rows before saving (atomic transaction: all valid rows or none).
   * Auto-adds unknown suppliers as draft suppliers pending confirmation.
   *
   * @param file Excel file to import
   * @return import result with success count, error count, error details, and error report ID
   */
  @PostMapping("/batch-import")
  @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT', 'CFO')")
  public ResponseEntity<Map<String, Object>> batchImport(
      @RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
    ImportResultDTO result = purchaseBillImportService.importBills(file);
    Map<String, Object> body = new HashMap<>();
    body.put("data", result);
    return ResponseEntity.ok(body);
  }

  /**
   * Generate Excel template for purchase bill import.
   * Requires authenticated user with Accountant+ role.
   *
   * @return Excel template file
   */
  @GetMapping("/import-template")
  @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT', 'CFO')")
  public ResponseEntity<byte[]> getImportTemplate() {
    byte[] template = purchaseBillImportService.generateTemplate();
    org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
    headers.setContentType(org.springframework.http.MediaType.APPLICATION_OCTET_STREAM);
    headers.setContentDispositionFormData("attachment", "purchase-bill-import-template.xlsx");
    headers.setContentLength(template.length);
    return ResponseEntity.ok().headers(headers).body(template);
  }

  /**
   * Upload attachment for a purchase bill.
   * Requires authenticated user with Accountant+ role.
   *
   * @param billId purchase bill ID
   * @param file file to upload
   * @return attachment DTO
   */
  @PostMapping("/{id}/attachments")
  @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT', 'CFO')")
  public ResponseEntity<Map<String, Object>> uploadAttachment(
      @PathVariable("id") UUID billId,
      @RequestParam("file") org.springframework.web.multipart.MultipartFile file) {

    try {
      PurchaseBillAttachmentDTO attachment = purchaseBillAttachmentService.uploadAttachment(billId, file);

      Map<String, Object> body = new HashMap<>();
      body.put("data", attachment);
      body.put("message", "Attachment uploaded successfully");

      return ResponseEntity.status(HttpStatus.CREATED).body(body);
    } catch (ResponseStatusException e) {
      throw e;
    }
  }

  /**
   * List all attachments for a purchase bill.
   * Requires authenticated user with Accountant+ role.
   *
   * @param billId purchase bill ID
   * @return list of attachment DTOs
   */
  @GetMapping("/{id}/attachments")
  @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT', 'CFO')")
  public ResponseEntity<Map<String, Object>> listAttachments(
      @PathVariable("id") UUID billId) {

    List<PurchaseBillAttachmentDTO> attachments = purchaseBillAttachmentService.listAttachments(billId);

    Map<String, Object> body = new HashMap<>();
    body.put("data", attachments);
    body.put("count", attachments.size());

    return ResponseEntity.ok(body);
  }

  /**
   * Generate signed URL for downloading an attachment.
   * Requires authenticated user with Accountant+ role.
   *
   * @param billId purchase bill ID
   * @param attachmentId attachment ID
   * @return redirect to signed URL
   */
  @GetMapping("/{id}/attachments/{attachmentId}/download")
  @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT', 'CFO')")
  public ResponseEntity<Void> downloadAttachment(
      @PathVariable("id") UUID billId,
      @PathVariable UUID attachmentId) {

    String signedUrl = purchaseBillAttachmentService.generateSignedUrl(billId, attachmentId);

    return ResponseEntity.status(HttpStatus.FOUND)
        .header(org.springframework.http.HttpHeaders.LOCATION, signedUrl)
        .build();
  }

  /**
   * Preview an attachment (logs view event).
   * Requires authenticated user with Accountant+ role.
   *
   * @param billId purchase bill ID
   * @param attachmentId attachment ID
   * @return redirect to signed URL
   */
  @GetMapping("/{id}/attachments/{attachmentId}/preview")
  @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT', 'CFO')")
  public ResponseEntity<Void> previewAttachment(
      @PathVariable("id") UUID billId,
      @PathVariable UUID attachmentId) {

    String signedUrl = purchaseBillAttachmentService.generateSignedUrl(billId, attachmentId);

    return ResponseEntity.status(HttpStatus.FOUND)
        .header(org.springframework.http.HttpHeaders.LOCATION, signedUrl)
        .build();
  }

  /**
   * Delete an attachment.
   * Requires authenticated user with Accountant+ role.
   * Only allowed for DRAFT bills by creator or admin.
   *
   * @param billId purchase bill ID
   * @param attachmentId attachment ID
   * @param request request body containing deletion reason
   * @return 204 No Content
   */
  @DeleteMapping("/{id}/attachments/{attachmentId}")
  @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT', 'CFO')")
  public ResponseEntity<Void> deleteAttachment(
      @PathVariable("id") UUID billId,
      @PathVariable UUID attachmentId,
      @RequestBody(required = false) Map<String, String> request) {

    String reason = request != null ? request.get("reason") : null;
    if (reason == null || reason.trim().isEmpty()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Deletion reason is required");
    }

    purchaseBillAttachmentService.deleteAttachment(billId, attachmentId, reason);

    return ResponseEntity.noContent().build();
  }
}
