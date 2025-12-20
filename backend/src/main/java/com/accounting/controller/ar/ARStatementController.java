package com.accounting.controller.ar;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.accounting.dto.ARReconciliationImportDTO;
import com.accounting.dto.ARStatementDisputeDTO;
import com.accounting.dto.ARStatementHistoryDTO;
import com.accounting.entity.ARStatementDispute;
import com.accounting.entity.ARStatementHistory;
import com.accounting.security.CompanyContext;
import com.accounting.security.SecurityUtils;
import com.accounting.service.ARDisputeService;
import com.accounting.service.ARReconciliationImportService;
import com.accounting.service.ARStatementService;
import com.accounting.service.AuditService;

import jakarta.servlet.http.HttpServletRequest;

/**
 * REST controller for AR statement operations. Provides endpoints for statement generation,
 * export, email, import, reconciliation, and dispute management.
 */
@RestController
@RequestMapping("/api/v1/ar-statements")
public class ARStatementController {

  private final ARStatementService statementService;
  private final ARReconciliationImportService reconciliationService;
  private final ARDisputeService disputeService;
  private final AuditService auditService;

  public ARStatementController(
      ARStatementService statementService,
      ARReconciliationImportService reconciliationService,
      ARDisputeService disputeService,
      AuditService auditService) {
    this.statementService = statementService;
    this.reconciliationService = reconciliationService;
    this.disputeService = disputeService;
    this.auditService = auditService;
  }

  /**
   * Get statement for customer (summary or detailed format).
   *
   * @param customerId customer ID
   * @param format statement format (SUMMARY or DETAILED, default: SUMMARY)
   * @param asOfDate as-of date (optional, defaults to today)
   * @param httpRequest HTTP request for audit logging
   * @return statement DTO
   */
  @GetMapping("/{customerId}")
  @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT', 'CFO')")
  public ResponseEntity<?> getStatement(
      @PathVariable Long customerId,
      @RequestParam(required = false, defaultValue = "SUMMARY") String format,
      @RequestParam(required = false) LocalDate asOfDate,
      HttpServletRequest httpRequest) {
    Long companyId = CompanyContext.getCompanyId();
    Long userId = SecurityUtils.getCurrentUserId();

    ARStatementHistory.StatementFormat statementFormat =
        "DETAILED".equalsIgnoreCase(format)
            ? ARStatementHistory.StatementFormat.DETAILED
            : ARStatementHistory.StatementFormat.SUMMARY;

    ARStatementService.StatementResult result =
        statementService.getStatementWithHistory(customerId, statementFormat, asOfDate);
    Object statement = result.getStatement();
    UUID statementId = result.getHistoryId();

    // Log audit event
    try {
      auditService.logStatementGenerated(
          companyId, userId, statementId, customerId, statementFormat.toString(), httpRequest);
    } catch (Exception e) {
      org.slf4j.LoggerFactory.getLogger(ARStatementController.class)
          .warn("Failed to log statement generation: {}", e.getMessage());
    }

    return ResponseEntity.ok(statement);
  }

  /**
   * Export statement to PDF or Excel.
   *
   * @param customerId customer ID
   * @param format export format (PDF or EXCEL, default: EXCEL)
   * @param statementFormat statement format (SUMMARY or DETAILED, default: SUMMARY)
   * @param asOfDate as-of date (optional)
   * @param httpRequest HTTP request for audit logging
   * @return file content as byte array
   */
  @GetMapping("/{customerId}/export")
  @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT', 'CFO')")
  public ResponseEntity<byte[]> exportStatement(
      @PathVariable Long customerId,
      @RequestParam(required = false, defaultValue = "EXCEL") String format,
      @RequestParam(required = false, defaultValue = "SUMMARY") String statementFormat,
      @RequestParam(required = false) LocalDate asOfDate,
      HttpServletRequest httpRequest) {
    Long companyId = CompanyContext.getCompanyId();
    Long userId = SecurityUtils.getCurrentUserId();

    ARStatementHistory.StatementFormat stmtFormat =
        "DETAILED".equalsIgnoreCase(statementFormat)
            ? ARStatementHistory.StatementFormat.DETAILED
            : ARStatementHistory.StatementFormat.SUMMARY;

    ARStatementService.ExportResult result =
        statementService.exportStatement(customerId, format, stmtFormat, asOfDate);
    byte[] data = result.getData();
    UUID statementId = result.getHistoryId();

    // Log audit event
    try {
      auditService.logStatementExported(companyId, userId, statementId, format, httpRequest);
    } catch (Exception e) {
      org.slf4j.LoggerFactory.getLogger(ARStatementController.class)
          .warn("Failed to log statement export: {}", e.getMessage());
    }

    HttpHeaders headers = new HttpHeaders();
    if ("EXCEL".equalsIgnoreCase(format)) {
      headers.setContentType(
          MediaType.parseMediaType(
              "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
      headers.setContentDispositionFormData("attachment", "statement-" + customerId + ".xlsx");
    } else {
      headers.setContentType(MediaType.APPLICATION_PDF);
      headers.setContentDispositionFormData("attachment", "statement-" + customerId + ".pdf");
    }

    return ResponseEntity.ok().headers(headers).body(data);
  }

  /**
   * Send statement to customer via email.
   *
   * @param customerId customer ID
   * @param email recipient email address
   * @param statementFormat statement format (SUMMARY or DETAILED, default: SUMMARY)
   * @param asOfDate as-of date (optional)
   * @param httpRequest HTTP request for audit logging
   * @return success response
   */
  @PostMapping("/{customerId}/send")
  @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT', 'CFO')")
  public ResponseEntity<Map<String, String>> sendStatement(
      @PathVariable Long customerId,
      @RequestParam String email,
      @RequestParam(required = false, defaultValue = "SUMMARY") String statementFormat,
      @RequestParam(required = false) LocalDate asOfDate,
      HttpServletRequest httpRequest) {
    Long companyId = CompanyContext.getCompanyId();
    Long userId = SecurityUtils.getCurrentUserId();

    ARStatementHistory.StatementFormat stmtFormat =
        "DETAILED".equalsIgnoreCase(statementFormat)
            ? ARStatementHistory.StatementFormat.DETAILED
            : ARStatementHistory.StatementFormat.SUMMARY;

    UUID statementId =
    statementService.sendStatementToCustomer(customerId, email, stmtFormat, asOfDate);

    // Log audit event
    try {
      auditService.logStatementSent(companyId, userId, statementId, 1, httpRequest);
    } catch (Exception e) {
      org.slf4j.LoggerFactory.getLogger(ARStatementController.class)
          .warn("Failed to log statement send: {}", e.getMessage());
    }

    Map<String, String> response = new HashMap<>();
    response.put("message", "Statement sent successfully");
    response.put("recipient", email);

    return ResponseEntity.ok(response);
  }

  /**
   * Import customer-provided reconciliation CSV.
   *
   * @param customerId customer ID
   * @param file CSV file with InvoiceNumber, CustomerAmount, CustomerPayment, Notes columns
   * @param httpRequest HTTP request for audit logging
   * @return reconciliation import result
   */
  @PostMapping("/{customerId}/import-reconciliation")
  @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT', 'CFO')")
  public ResponseEntity<ARReconciliationImportDTO> importReconciliation(
      @PathVariable Long customerId,
      @RequestParam MultipartFile file,
      HttpServletRequest httpRequest) {
    Long companyId = CompanyContext.getCompanyId();
    Long userId = SecurityUtils.getCurrentUserId();

    ARReconciliationImportDTO result = reconciliationService.importReconciliation(customerId, file);

    // Log audit event
    try {
      // Note: Using customerId as supplierId parameter (AP method signature)
      auditService.logStatementImported(
          companyId,
          userId,
          customerId, // Using customerId where supplierId is expected
          result.getMatchedCount(),
          result.getMismatchCount(),
          httpRequest);
    } catch (Exception e) {
      org.slf4j.LoggerFactory.getLogger(ARStatementController.class)
          .warn("Failed to log reconciliation import: {}", e.getMessage());
    }

    return ResponseEntity.ok(result);
  }

  /**
   * Get statement history for a customer.
   *
   * @param customerId customer ID
   * @param page page number (0-based, default: 0)
   * @param size page size (default: 20, max: 100)
   * @return paginated statement history
   */
  @GetMapping("/{customerId}/history")
  @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT', 'CFO')")
  public ResponseEntity<Map<String, Object>> getStatementHistory(
      @PathVariable Long customerId,
      @RequestParam(required = false, defaultValue = "0") int page,
      @RequestParam(required = false, defaultValue = "20") int size) {

    if (size > 100) {
      size = 100;
    }

    Pageable pageable =
        PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "generatedAt"));
    Page<ARStatementHistoryDTO> historyPage =
        statementService.getStatementHistory(customerId, pageable);

    Map<String, Object> response = new HashMap<>();
    response.put("history", historyPage.getContent());
    response.put("currentPage", historyPage.getNumber());
    response.put("totalItems", historyPage.getTotalElements());
    response.put("totalPages", historyPage.getTotalPages());

    return ResponseEntity.ok(response);
  }

  /**
   * Batch export statements for multiple customers as ZIP.
   *
   * @param customerIds comma-separated list of customer IDs
   * @param format export format (PDF or EXCEL, default: EXCEL)
   * @param statementFormat statement format (SUMMARY or DETAILED, default: SUMMARY)
   * @param asOfDate as-of date (optional)
   * @return ZIP file content
   */
  @GetMapping("/batch-export")
  @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT', 'CFO')")
  public ResponseEntity<byte[]> batchExportStatements(
      @RequestParam String customerIds,
      @RequestParam(required = false, defaultValue = "EXCEL") String format,
      @RequestParam(required = false, defaultValue = "SUMMARY") String statementFormat,
      @RequestParam(required = false) LocalDate asOfDate) {

    // Validate input
    if (customerIds == null || customerIds.trim().isEmpty()) {
      throw new IllegalArgumentException("customerIds parameter is required");
    }

    // Parse and validate customer IDs
    List<Long> customerIdList;
    try {
      customerIdList =
        java.util.Arrays.stream(customerIds.split(","))
            .map(String::trim)
              .filter(s -> !s.isEmpty())
            .map(Long::parseLong)
            .toList();
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException(
          "Invalid customerIds format. Expected comma-separated numeric IDs: " + e.getMessage());
    }

    if (customerIdList.isEmpty()) {
      throw new IllegalArgumentException("At least one customer ID is required");
    }

    ARStatementHistory.StatementFormat stmtFormat =
        "DETAILED".equalsIgnoreCase(statementFormat)
            ? ARStatementHistory.StatementFormat.DETAILED
            : ARStatementHistory.StatementFormat.SUMMARY;

    byte[] zipData =
        statementService.batchExportStatements(customerIdList, format, stmtFormat, asOfDate);

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
    headers.setContentDispositionFormData("attachment", "statements-batch.zip");

    return ResponseEntity.ok().headers(headers).body(zipData);
  }

  /**
   * Get disputes with filters.
   *
   * @param page page number (0-based, default: 0)
   * @param size page size (default: 20, max: 100)
   * @param customerId optional customer filter
   * @param status optional status filter (OPEN or RESOLVED)
   * @param dateFrom optional start date filter
   * @param dateTo optional end date filter
   * @return paginated disputes
   */
  @GetMapping("/disputes")
  @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT', 'CFO')")
  public ResponseEntity<Map<String, Object>> getDisputes(
      @RequestParam(required = false, defaultValue = "0") int page,
      @RequestParam(required = false, defaultValue = "20") int size,
      @RequestParam(required = false) Long customerId,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) Instant dateFrom,
      @RequestParam(required = false) Instant dateTo) {

    if (size > 100) {
      size = 100;
    }

    ARStatementDispute.DisputeStatus disputeStatus = null;
    if (status != null) {
      try {
        disputeStatus = ARStatementDispute.DisputeStatus.valueOf(status.toUpperCase());
      } catch (IllegalArgumentException e) {
        // Invalid status, ignore
      }
    }

    Pageable pageable =
        PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
    Page<ARStatementDisputeDTO> disputesPage =
        disputeService.getDisputes(customerId, disputeStatus, dateFrom, dateTo, pageable);

    Map<String, Object> response = new HashMap<>();
    response.put("disputes", disputesPage.getContent());
    response.put("currentPage", disputesPage.getNumber());
    response.put("totalItems", disputesPage.getTotalElements());
    response.put("totalPages", disputesPage.getTotalPages());

    return ResponseEntity.ok(response);
  }

  /**
   * Resolve dispute with resolution notes.
   *
   * @param disputeId dispute ID
   * @param request request body with resolutionNotes
   * @param httpRequest HTTP request for audit logging
   * @return success response
   */
  @PostMapping("/disputes/{disputeId}/resolve")
  @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT', 'CFO')")
  public ResponseEntity<Map<String, String>> resolveDispute(
      @PathVariable UUID disputeId,
      @RequestBody Map<String, String> request,
      HttpServletRequest httpRequest) {
    Long companyId = CompanyContext.getCompanyId();
    Long userId = SecurityUtils.getCurrentUserId();

    String resolutionNotes = request.get("resolutionNotes");
    if (resolutionNotes == null || resolutionNotes.trim().isEmpty()) {
      throw new IllegalArgumentException("Resolution notes are required");
    }

    disputeService.resolveDispute(disputeId, resolutionNotes);

    // Log audit event
    try {
      auditService.logDisputeUpdated(
          companyId, userId, disputeId, "OPEN", "RESOLVED", httpRequest);
    } catch (Exception e) {
      org.slf4j.LoggerFactory.getLogger(ARStatementController.class)
          .warn("Failed to log dispute resolution: {}", e.getMessage());
    }

    Map<String, String> response = new HashMap<>();
    response.put("message", "Dispute resolved successfully");
    response.put("status", "RESOLVED");

    return ResponseEntity.ok(response);
  }
}
