package com.accounting.controller.voucher;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.accounting.dto.ApplyVoucherTemplateRequest;
import com.accounting.dto.PostVoucherRequest;
import com.accounting.dto.PostVoucherResponse;
import com.accounting.dto.ReverseVoucherRequest;
import com.accounting.dto.UnpostVoucherRequest;
import com.accounting.dto.VoucherAttachmentDTO;
import com.accounting.dto.VoucherCountDTO;
import com.accounting.dto.VoucherCreateRequest;
import com.accounting.dto.VoucherDTO;
import com.accounting.dto.VoucherHistoryEntryDTO;
import com.accounting.dto.VoucherLineDTO;
import com.accounting.dto.VoucherListDTO;
import com.accounting.dto.VoucherTemplateDTO;
import com.accounting.dto.VoucherValidationResult;
import com.accounting.exception.VoucherPostingException;
import com.accounting.security.CompanyContext;
import com.accounting.security.SecurityUtils;
import com.accounting.service.AuditService;
import com.accounting.service.VoucherHistoryExportService;
import com.accounting.service.VoucherHistoryService;
import com.accounting.service.VoucherService;
import com.accounting.service.VoucherTemplateService;
import com.accounting.service.VoucherValidationService;
import com.accounting.service.voucher.VoucherAttachmentService;
import com.accounting.service.voucher.VoucherPostingService;
import com.accounting.service.voucher.VoucherReversalService;
import com.accounting.service.voucher.VoucherUnpostingService;

import jakarta.persistence.OptimisticLockException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/**
 * REST controller for Voucher operations.
 * All authenticated users with Accountant+ role can view vouchers; delete
 * requires Accountant+ role.
 */
@RestController
@RequestMapping("/api/v1/vouchers")
public class VoucherController {

  private static final Logger logger = LoggerFactory.getLogger(VoucherController.class);

  private final VoucherService voucherService;
  private final VoucherValidationService voucherValidationService;
  private final VoucherTemplateService voucherTemplateService;
  private final VoucherPostingService voucherPostingService;
  private final VoucherUnpostingService voucherUnpostingService;
  private final VoucherReversalService voucherReversalService;
  private final VoucherHistoryService voucherHistoryService;
  private final VoucherHistoryExportService voucherHistoryExportService;
  private final VoucherAttachmentService voucherAttachmentService;
  private final AuditService auditService;

  public VoucherController(
      VoucherService voucherService,
      VoucherValidationService voucherValidationService,
      VoucherTemplateService voucherTemplateService,
      VoucherPostingService voucherPostingService,
      VoucherUnpostingService voucherUnpostingService,
      VoucherReversalService voucherReversalService,
      VoucherHistoryService voucherHistoryService,
      VoucherHistoryExportService voucherHistoryExportService,
      VoucherAttachmentService voucherAttachmentService,
      AuditService auditService) {
    this.voucherService = voucherService;
    this.voucherValidationService = voucherValidationService;
    this.voucherTemplateService = voucherTemplateService;
    this.voucherPostingService = voucherPostingService;
    this.voucherUnpostingService = voucherUnpostingService;
    this.voucherReversalService = voucherReversalService;
    this.voucherHistoryService = voucherHistoryService;
    this.voucherHistoryExportService = voucherHistoryExportService;
    this.voucherAttachmentService = voucherAttachmentService;
    this.auditService = auditService;
  }

  /**
   * Get paginated, filtered, sorted voucher list.
   * Supports query params: page, size, status, dateFrom, dateTo, search, sort.
   * Requires authenticated user with Accountant+ role.
   *
   * @param page     page number (0-based, default: 0)
   * @param size     page size (default: 20, max: 50)
   * @param status   filter by status (optional: draft, posted, unposted)
   * @param dateFrom filter by date from (optional, format: YYYY-MM-DD)
   * @param dateTo   filter by date to (optional, format: YYYY-MM-DD)
   * @param search   search term for voucher number or description (optional,
   *                 supports Vietnamese unaccented matching)
   * @param sort     sort parameters (optional, format: field,direction e.g.,
   *                 date,desc or status,asc)
   * @return paginated voucher list
   */
  @GetMapping
  @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT', 'CFO')")
  public ResponseEntity<Map<String, Object>> getVouchers(
      Pageable pageable,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) LocalDate dateFrom,
      @RequestParam(required = false) LocalDate dateTo,
      @RequestParam(required = false) String search,
      @RequestParam(required = false) Long accountId) {

    // Validate page size (max 50) - create new pageable if needed
    if (pageable.getPageSize() > 50) {
      pageable = PageRequest.of(pageable.getPageNumber(), 50, pageable.getSort());
    }
    
    // Debug logging
    logger.info("[VoucherController] Pageable sort: {}", pageable.getSort());

    // Call service
    Page<VoucherListDTO> vouchers = voucherService.findAll(pageable, status, dateFrom, dateTo, search, accountId);

    // Build response matching spec format: { data: { content: VoucherDTO[],
    // totalElements: number, totalPages: number }, meta: {...} }
    Map<String, Object> data = new HashMap<>();
    data.put("content", vouchers.getContent());
    data.put("totalElements", vouchers.getTotalElements());
    data.put("totalPages", vouchers.getTotalPages());

    Map<String, Object> meta = new HashMap<>();
    meta.put("page", vouchers.getNumber());
    meta.put("size", vouchers.getSize());
    meta.put("totalElements", vouchers.getTotalElements());
    meta.put("totalPages", vouchers.getTotalPages());

    Map<String, Object> body = new HashMap<>();
    body.put("data", data);
    body.put("meta", meta);

    return ResponseEntity.ok(body);
  }

  /**
   * Apply a voucher template and return preview voucher data.
   */
  @PostMapping("/apply-template")
  @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT', 'CFO')")
  public ResponseEntity<Map<String, Object>> applyTemplate(
      @Valid @RequestBody ApplyVoucherTemplateRequest request) {
    VoucherTemplateDTO template = voucherTemplateService
        .getById(request.getTemplateId())
        .orElseThrow(
            () -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Voucher template not found"));

    VoucherDTO previewVoucher = buildTemplatePreviewVoucher(template, request);

    Map<String, Object> data = new HashMap<>();
    data.put("template", template);
    data.put("voucher", previewVoucher);

    Map<String, Object> body = new HashMap<>();
    body.put("data", data);

    return ResponseEntity.ok(body);
  }

  /**
   * Get voucher by ID (with lines).
   * Requires authenticated user with Accountant+ role.
   *
   * @param voucherId voucher ID
   * @return voucher details with lines array
   */
  @GetMapping("/{voucherId}")
  @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT', 'CFO')")
  public ResponseEntity<Map<String, Object>> getVoucherById(@PathVariable UUID voucherId) {
    return voucherService
        .getVoucherById(voucherId)
        .map(
            voucher -> {
              Map<String, Object> body = new HashMap<>();
              body.put("data", voucher);
              return ResponseEntity.ok(body);
            })
        .orElse(ResponseEntity.notFound().build());
  }

  /**
   * Create a new voucher with line items.
   * Requires authenticated user with Accountant+ role.
   *
   * @param request voucher create request with line items
   * @return created voucher DTO
   */
  @PostMapping
  @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT', 'CFO')")
  public ResponseEntity<Map<String, Object>> createVoucher(
      @Valid @RequestBody VoucherCreateRequest request) {
    VoucherDTO voucher = voucherService.create(request);
    Map<String, Object> body = new HashMap<>();
    body.put("data", voucher);
    return ResponseEntity.status(HttpStatus.CREATED).body(body);
  }

  /**
   * Update an existing draft voucher.
   * Only draft vouchers can be updated.
   * Requires authenticated user with Accountant+ role.
   *
   * @param voucherId voucher ID
   * @param request   voucher update request with line items
   * @return updated voucher DTO
   */
  @PutMapping("/{voucherId}")
  @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT', 'CFO')")
  public ResponseEntity<Map<String, Object>> updateVoucher(
      @PathVariable UUID voucherId, @Valid @RequestBody VoucherCreateRequest request) {
    try {
      VoucherDTO voucher = voucherService.update(voucherId, request);
      Map<String, Object> body = new HashMap<>();
      body.put("data", voucher);
      return ResponseEntity.ok(body);
    } catch (OptimisticLockException e) {
      // Handle optimistic locking conflict - voucher was modified by another user
      Map<String, Object> errorBody = new HashMap<>();
      errorBody.put("error", "Voucher was modified by another user. Please refresh and try again.");
      errorBody.put("code", "OPTIMISTIC_LOCK_EXCEPTION");
      return ResponseEntity.status(HttpStatus.CONFLICT).body(errorBody);
    }
  }

  /**
   * Validate a voucher (without saving).
   * Returns detailed validation errors for client-side display.
   * For new vouchers, use a placeholder UUID (e.g., all zeros).
   * Requires authenticated user with Accountant+ role.
   *
   * @param voucherId voucher ID (use placeholder UUID for new vouchers)
   * @param request   voucher request to validate
   * @return validation result with errors (if any)
   */
  @PostMapping("/{voucherId}/validate")
  @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT', 'CFO')")
  public ResponseEntity<Map<String, Object>> validateVoucher(
      @PathVariable UUID voucherId,
      @Valid @RequestBody VoucherCreateRequest request) {
    // Note: voucherId is in path per story requirements, but validation doesn't
    // require it
    // For new vouchers, clients should use a placeholder UUID
    VoucherValidationResult result = voucherValidationService.validate(request);
    Map<String, Object> body = new HashMap<>();
    body.put("valid", result.isValid());
    body.put("errors", result.getErrors());
    return ResponseEntity.ok(body);
  }

  /**
   * Get voucher counts by status (for badges).
   * Requires authenticated user with Accountant+ role.
   *
   * @return voucher counts
   */
  @GetMapping("/counts")
  @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT', 'CFO')")
  public ResponseEntity<Map<String, Object>> getVoucherCounts() {
    VoucherCountDTO counts = voucherService.getCounts();

    Map<String, Object> body = new HashMap<>();
    body.put("data", counts);

    return ResponseEntity.ok(body);
  }

  /**
   * Get voucher counts by status (for badges) - alias endpoint matching spec.
   * Requires authenticated user with Accountant+ role.
   *
   * @return voucher counts
   */
  @GetMapping("/count")
  @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT', 'CFO')")
  public ResponseEntity<Map<String, Object>> getVoucherCount() {
    return getVoucherCounts();
  }

  /**
   * Post a voucher (change status from DRAFT to POSTED).
   * Generates journal entries atomically in a single transaction.
   * Requires Chief Accountant+ role.
   *
   * @param voucherId voucher ID to post
   * @param request   posting request (optional validateOnly flag)
   * @return posted voucher and generated journal entries
   */
  @PostMapping("/{voucherId}/post")
  @PreAuthorize("hasAnyRole('CHIEF_ACCOUNTANT', 'ADMIN', 'CFO')")
  public ResponseEntity<Map<String, Object>> postVoucher(
      @PathVariable UUID voucherId,
      @RequestBody(required = false) PostVoucherRequest request,
      HttpServletRequest httpRequest) {
    try {
      PostVoucherResponse response = voucherPostingService.postVoucher(voucherId, httpRequest);
      Map<String, Object> body = new HashMap<>();
      body.put("data", response);
      return ResponseEntity.ok(body);
    } catch (VoucherPostingException e) {
      // Return 400 with detailed validation error map
      Map<String, Object> errorBody = new HashMap<>();
      errorBody.put("error", e.getMessage());
      errorBody.put("validationErrors", e.getValidationErrors());
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorBody);
    }
  }

  /**
   * Unpost a voucher (change status from POSTED to DRAFT).
   * Deletes journal entries atomically in a single transaction.
   * Requires Chief Accountant+ role.
   *
   * @param voucherId voucher ID to unpost
   * @param request   unposting request with reason (required for audit)
   * @return unposted voucher
   */
  @PostMapping("/{voucherId}/unpost")
  @PreAuthorize("hasAnyRole('CHIEF_ACCOUNTANT', 'ADMIN', 'CFO')")
  public ResponseEntity<Map<String, Object>> unpostVoucher(
      @PathVariable UUID voucherId,
      @Valid @RequestBody UnpostVoucherRequest request,
      HttpServletRequest httpRequest) {
    VoucherDTO voucher = voucherUnpostingService.unpostVoucher(voucherId, request.getReason(), httpRequest);
    Map<String, Object> body = new HashMap<>();
    body.put("data", voucher);
    return ResponseEntity.ok(body);
  }

  /**
   * Reverse a posted voucher.
   * Creates a new reversal voucher with REV-{original_number} format,
   * swaps debit/credit amounts, links bi-directionally, and auto-posts.
   * Requires Chief Accountant+ role.
   *
   * @param voucherId voucher ID to reverse
   * @param request   reversal request with description and reason
   * @return both original and reversal vouchers
   */
  @PostMapping("/{voucherId}/reverse")
  @PreAuthorize("hasAnyRole('CHIEF_ACCOUNTANT', 'ADMIN', 'CFO')")
  public ResponseEntity<Map<String, Object>> reverseVoucher(
      @PathVariable UUID voucherId,
      @Valid @RequestBody ReverseVoucherRequest request,
      HttpServletRequest httpRequest) {
    VoucherReversalService.ReversalResult result = voucherReversalService.reverseVoucher(
        voucherId, request.getDescription(), request.getReason(), httpRequest);
    Map<String, Object> data = new HashMap<>();
    data.put("original", result.getOriginal());
    data.put("reversal", result.getReversal());
    Map<String, Object> body = new HashMap<>();
    body.put("data", data);
    return ResponseEntity.ok(body);
  }

  /**
   * Get voucher history (audit log entries).
   * Requires authenticated user with Accountant+ role.
   *
   * @param voucherId voucher ID
   * @return list of voucher history entries
   */
  @GetMapping("/{voucherId}/history")
  @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT', 'CFO')")
  public ResponseEntity<Map<String, Object>> getVoucherHistory(@PathVariable UUID voucherId) {
    // Verify voucher exists and user has access
    voucherService.getVoucherById(voucherId)
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.NOT_FOUND, "Voucher not found: " + voucherId));

    List<VoucherHistoryEntryDTO> history = voucherHistoryService.getVoucherHistory(voucherId);
    Map<String, Object> body = new HashMap<>();
    body.put("voucherId", voucherId);
    body.put("history", history);
    body.put("count", history.size());
    return ResponseEntity.ok(body);
  }

  /**
   * Export voucher history in JSON or PDF format.
   * Requires authenticated user with Accountant+ role.
   *
   * @param voucherId voucher ID
   * @param format    export format (json or pdf, default: json)
   * @return exported voucher history
   */
  @GetMapping("/{voucherId}/history/export")
  @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT', 'CFO')")
  public ResponseEntity<?> exportVoucherHistory(
      @PathVariable UUID voucherId,
      @RequestParam(required = false, defaultValue = "json") String format) {
    // Verify voucher exists and user has access
    voucherService.getVoucherById(voucherId)
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.NOT_FOUND, "Voucher not found: " + voucherId));

    List<VoucherHistoryEntryDTO> history = voucherHistoryService.getVoucherHistory(voucherId);

    if ("pdf".equalsIgnoreCase(format)) {
      try {
        byte[] pdfBytes = voucherHistoryExportService.exportAsPdf(voucherId, history);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", "voucher-history-" + voucherId + ".pdf");
        headers.setContentLength(pdfBytes.length);
        return ResponseEntity.ok()
            .headers(headers)
            .body(pdfBytes);
      } catch (Exception e) {
        throw new ResponseStatusException(
            HttpStatus.INTERNAL_SERVER_ERROR, "Failed to generate PDF: " + e.getMessage());
      }
    } else {
      // JSON export
      String json = voucherHistoryExportService.exportAsJson(voucherId, history);
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);
      headers.setContentDispositionFormData("attachment", "voucher-history-" + voucherId + ".json");
      return ResponseEntity.ok()
          .headers(headers)
          .body(json);
    }
  }

  /**
   * Delete voucher with validation.
   * Only draft vouchers that are not referenced can be deleted.
   * Posted vouchers cannot be deleted (returns 409 Conflict).
   * Requires deletion reason.
   * Requires authenticated user with Accountant+ role.
   *
   * @param voucherId voucher ID
   * @param reason    deletion reason (required)
   * @return success response
   */
  @DeleteMapping("/{voucherId}")
  @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT', 'CFO')")
  public ResponseEntity<Map<String, Object>> deleteVoucher(
      @PathVariable UUID voucherId,
      @RequestParam(required = false) String reason,
      HttpServletRequest request) {

    // Validate reason is provided
    if (reason == null || reason.isBlank()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Deletion reason is required");
    }

    // Delete voucher (pass request for audit logging)
    voucherService.delete(voucherId, reason, request);

    Map<String, Object> body = new HashMap<>();
    body.put("message", "Voucher deleted successfully");
    body.put("voucherId", voucherId);

    return ResponseEntity.ok(body);
  }

  private VoucherDTO buildTemplatePreviewVoucher(
      VoucherTemplateDTO template, ApplyVoucherTemplateRequest request) {
    VoucherDTO dto = new VoucherDTO();
    dto.setId(null);
    dto.setCompanyId(CompanyContext.getCompanyId());
    dto.setVoucherNumber(null);
    dto.setVoucherDate(request.getVoucherDate());
    dto.setPeriodId(null);
    dto.setDescription(
        request.getDescription() != null && !request.getDescription().isBlank()
            ? request.getDescription()
            : template.getDescription());
    dto.setStatus("draft");
    dto.setCurrency("VND");
    dto.setTotalDebit(BigDecimal.ZERO);
    dto.setTotalCredit(BigDecimal.ZERO);
    dto.setEnteredBy(null);
    dto.setEnteredByName(null);
    dto.setPostedBy(null);
    dto.setPostedByName(null);
    dto.setPostedAt(null);
    dto.setReversalOf(null);
    dto.setReversedBy(null);
    dto.setReversedByName(null);
    dto.setCreatedAt(Instant.now());
    dto.setUpdatedAt(Instant.now());
    dto.setVersion(0L);
    dto.setAttachmentCount(0);
    dto.setLines(buildLinesFromTemplate(template));
    return dto;
  }

  private List<VoucherLineDTO> buildLinesFromTemplate(VoucherTemplateDTO template) {
    List<VoucherLineDTO> lines = new ArrayList<>();
    if (template.getLines() == null) {
      return lines;
    }
    int lineNumber = 1;
    for (var templateLine : template.getLines()) {
      if (templateLine.getDebitAccountId() != null) {
        VoucherLineDTO debitLine = new VoucherLineDTO();
        debitLine.setLineNumber(lineNumber++);
        debitLine.setAccountId(templateLine.getDebitAccountId());
        debitLine.setDebit(BigDecimal.ZERO);
        debitLine.setCredit(BigDecimal.ZERO);
        debitLine.setDescription(templateLine.getDefaultDescription());
        lines.add(debitLine);
      }
      if (templateLine.getCreditAccountId() != null) {
        VoucherLineDTO creditLine = new VoucherLineDTO();
        creditLine.setLineNumber(lineNumber++);
        creditLine.setAccountId(templateLine.getCreditAccountId());
        creditLine.setDebit(BigDecimal.ZERO);
        creditLine.setCredit(BigDecimal.ZERO);
        creditLine.setDescription(templateLine.getDefaultDescription());
        lines.add(creditLine);
      }
    }
    return lines;
  }

  /**
   * Upload attachment for a voucher.
   * Requires authenticated user with Accountant+ role.
   *
   * @param voucherId voucher ID
   * @param file      file to upload
   * @return attachment DTO
   */
  @PostMapping("/{voucherId}/attachments")
  @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT', 'CFO')")
  public ResponseEntity<Map<String, Object>> uploadAttachment(
      @PathVariable UUID voucherId,
      @RequestParam("file") MultipartFile file,
      HttpServletRequest request) {

    try {
    VoucherAttachmentDTO attachment = voucherAttachmentService.uploadAttachment(voucherId, file);

    Map<String, Object> body = new HashMap<>();
    body.put("data", attachment);
    body.put("message", "Attachment uploaded successfully");

    return ResponseEntity.status(HttpStatus.CREATED).body(body);
    } catch (ResponseStatusException e) {
      // Log unsupported file type, size violation, or virus scan failure attempts
      if (e.getStatusCode() == HttpStatus.BAD_REQUEST && file != null) {
        Long userId = SecurityUtils.getCurrentUserId();
        String fileName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "unknown";
        String contentType = file.getContentType() != null ? file.getContentType() : "unknown";
        Long fileSize = file.getSize();
        
        // Build detailed reason with file metadata
        String reason = e.getReason() != null ? e.getReason() : "File upload blocked";
        String detailedReason = String.format("%s (file: %s, type: %s, size: %d bytes)", 
            reason, fileName, contentType, fileSize);
        
        // Determine attempt type based on error message
        String attemptType = "UNSUPPORTED_FILE_TYPE_OR_SIZE";
        if (reason != null && reason.toLowerCase().contains("virus scan")) {
          attemptType = "VIRUS_SCAN_FAILED";
        }
        
        // Log blocked upload attempt
        auditService.logBlockedAttempt(
            userId,
            null, // accountId - not applicable for file uploads
            null, // accountCode - not applicable
            0, // lineNumber - not applicable
            "file", // fieldName
            detailedReason,
            attemptType,
            request);
      }
      throw e;
    }
  }

  /**
   * List all attachments for a voucher.
   * Requires authenticated user with Accountant+ role.
   *
   * @param voucherId voucher ID
   * @return list of attachment DTOs
   */
  @GetMapping("/{voucherId}/attachments")
  @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT', 'CFO')")
  public ResponseEntity<Map<String, Object>> listAttachments(
      @PathVariable UUID voucherId) {

    List<VoucherAttachmentDTO> attachments = voucherAttachmentService.listAttachments(voucherId);

    Map<String, Object> body = new HashMap<>();
    body.put("data", attachments);
    body.put("count", attachments.size());

    return ResponseEntity.ok(body);
  }

  /**
   * Generate signed URL for downloading an attachment.
   * Requires authenticated user with Accountant+ role.
   *
   * @param voucherId voucher ID
   * @param attachmentId attachment ID
   * @return redirect to signed URL
   */
  @GetMapping("/{voucherId}/attachments/{attachmentId}/download")
  @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT', 'CFO')")
  public ResponseEntity<Void> downloadAttachment(
      @PathVariable UUID voucherId,
      @PathVariable UUID attachmentId,
      HttpServletRequest request) {

    String signedUrl = voucherAttachmentService.generateSignedUrl(voucherId, attachmentId);

    // Load attachment for audit logging
    List<VoucherAttachmentDTO> attachments = voucherAttachmentService.listAttachments(voucherId);
    VoucherAttachmentDTO attachment = attachments.stream()
        .filter(a -> a.getId().equals(attachmentId))
        .findFirst()
        .orElse(null);

    if (attachment != null) {
      Long userId = SecurityUtils.getCurrentUserId();
      auditService.logAttachmentDownload(
          attachmentId,
          voucherId,
          attachment.getFileName(),
          attachment.getFileSize(),
          attachment.getMimeType(),
          userId,
          request);
    }

    return ResponseEntity.status(HttpStatus.FOUND)
        .header(HttpHeaders.LOCATION, signedUrl)
        .build();
    }

  /**
   * Preview an attachment (logs view event).
   * Requires authenticated user with Accountant+ role.
   *
   * @param voucherId voucher ID
   * @param attachmentId attachment ID
   * @param request HTTP request for IP address
   * @return redirect to signed URL
   */
  @GetMapping("/{voucherId}/attachments/{attachmentId}/preview")
  @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT', 'CFO')")
  public ResponseEntity<Void> previewAttachment(
      @PathVariable UUID voucherId,
      @PathVariable UUID attachmentId,
      HttpServletRequest request) {

    String signedUrl = voucherAttachmentService.generateSignedUrl(voucherId, attachmentId);

    // Load attachment for audit logging
    List<VoucherAttachmentDTO> attachments = voucherAttachmentService.listAttachments(voucherId);
    VoucherAttachmentDTO attachment = attachments.stream()
        .filter(a -> a.getId().equals(attachmentId))
        .findFirst()
        .orElse(null);

    if (attachment != null) {
      Long userId = SecurityUtils.getCurrentUserId();
      auditService.logAttachmentView(
          attachmentId,
          voucherId,
          attachment.getFileName(),
          attachment.getFileSize(),
          attachment.getMimeType(),
          userId,
          request);
    }

    return ResponseEntity.status(HttpStatus.FOUND)
        .header(HttpHeaders.LOCATION, signedUrl)
        .build();
    }

  /**
   * Delete an attachment.
   * Requires authenticated user with Accountant+ role.
   * Only allowed for DRAFT vouchers by creator or admin.
   *
   * @param voucherId voucher ID
   * @param attachmentId attachment ID
   * @param request request body containing deletion reason
   * @return 204 No Content
   */
  @DeleteMapping("/{voucherId}/attachments/{attachmentId}")
  @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT', 'CFO')")
  public ResponseEntity<Void> deleteAttachment(
      @PathVariable UUID voucherId,
      @PathVariable UUID attachmentId,
      @RequestBody(required = false) Map<String, String> request,
      HttpServletRequest httpRequest) {

    String reason = request != null ? request.get("reason") : null;
    if (reason == null || reason.trim().isEmpty()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Deletion reason is required");
    }

    // Load attachment before deletion for audit logging
    List<VoucherAttachmentDTO> attachments = voucherAttachmentService.listAttachments(voucherId);
    VoucherAttachmentDTO attachment = attachments.stream()
        .filter(a -> a.getId().equals(attachmentId))
        .findFirst()
        .orElse(null);

    voucherAttachmentService.deleteAttachment(voucherId, attachmentId, reason);

    // Log delete event in audit trail
    if (attachment != null) {
      Long userId = SecurityUtils.getCurrentUserId();
      auditService.logAttachmentDelete(
          attachmentId,
          voucherId,
          attachment.getFileName(),
          attachment.getFileSize(),
          attachment.getMimeType(),
          reason,
          userId,
          httpRequest);
    }

    return ResponseEntity.noContent().build();
  }
}
