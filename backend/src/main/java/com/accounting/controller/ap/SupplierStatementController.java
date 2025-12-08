package com.accounting.controller.ap;

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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.accounting.dto.DetailedStatementDTO;
import com.accounting.dto.GenerateStatementRequest;
import com.accounting.dto.ReconciliationResultDTO;
import com.accounting.dto.SupplierStatementDTO;
import com.accounting.dto.SupplierStatementDisputeDTO;
import com.accounting.dto.SupplierStatementHistoryDTO;
import com.accounting.dto.UpdateDisputeRequest;
import com.accounting.entity.SupplierStatementHistory;
import com.accounting.security.CompanyContext;
import com.accounting.security.SecurityUtils;
import com.accounting.service.AuditService;
import com.accounting.service.SupplierStatementService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/**
 * REST controller for supplier statement operations. Provides endpoints for statement generation,
 * export, email, import, reconciliation, and dispute management.
 */
@RestController
@RequestMapping("/api/v1/supplier-statements")
public class SupplierStatementController {

  private final SupplierStatementService statementService;
  private final AuditService auditService;

  public SupplierStatementController(
      SupplierStatementService statementService, AuditService auditService) {
    this.statementService = statementService;
    this.auditService = auditService;
  }

  /**
   * Generate supplier statement (summary or detailed).
   *
   * @param request generation request with supplier, date range, type, format
   * @param httpRequest HTTP request for audit logging
   * @return supplier statement DTO or detailed statement DTO
   */
  @PostMapping("/generate")
  @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT', 'CFO')")
  public ResponseEntity<?> generateStatement(
      @Valid @RequestBody GenerateStatementRequest request,
      HttpServletRequest httpRequest) {
    Long companyId = CompanyContext.getCompanyId();
    Long userId = SecurityUtils.getCurrentUserId();
    
    Object statement;
    UUID statementId;
    
    if (request.getStatementType() == SupplierStatementHistory.StatementType.SUMMARY) {
      SupplierStatementDTO summaryStatement =
          statementService.generateSummaryStatement(
              request.getSupplierId(), request.getStartDate(), request.getEndDate());
      summaryStatement.setFormat(request.getFormat());
      statementId = summaryStatement.getId();
      statement = summaryStatement;
    } else {
      DetailedStatementDTO detailedStatement =
          statementService.generateDetailedStatement(
              request.getSupplierId(), request.getStartDate(), request.getEndDate());
      detailedStatement.setFormat(request.getFormat());
      statementId = detailedStatement.getId();
      statement = detailedStatement;
    }

    // Log audit event
    try {
      auditService.logStatementGenerated(
          companyId,
          userId,
          statementId,
          request.getSupplierId(),
          request.getStatementType().toString(),
          httpRequest);
    } catch (Exception e) {
      // Log error but don't fail the request
      org.slf4j.LoggerFactory.getLogger(SupplierStatementController.class)
          .warn("Failed to log statement generation: {}", e.getMessage());
    }

    return ResponseEntity.ok(statement);
  }


  /**
   * Get statement by ID.
   *
   * @param id statement ID
   * @return statement history DTO
   */
  @GetMapping("/{id}")
  @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT', 'CFO')")
  public ResponseEntity<SupplierStatementHistoryDTO> getStatement(@PathVariable UUID id) {
    SupplierStatementHistoryDTO statement = statementService.getStatementById(id);
    return ResponseEntity.ok(statement);
  }

  /**
   * List statements with pagination and filters.
   *
   * @param page page number (0-based, default: 0)
   * @param size page size (default: 20, max: 100)
   * @param supplier filter by supplier ID (optional)
   * @param startDate filter by start date (optional)
   * @param endDate filter by end date (optional)
   * @param statementType filter by statement type (optional)
   * @param sort sort parameters (optional)
   * @return paginated statement history
   */
  @GetMapping
  @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT', 'CFO')")
  public ResponseEntity<Map<String, Object>> listStatements(
      @RequestParam(required = false, defaultValue = "0") int page,
      @RequestParam(required = false, defaultValue = "20") int size,
      @RequestParam(required = false) Long supplier,
      @RequestParam(required = false) LocalDate startDate,
      @RequestParam(required = false) LocalDate endDate,
      @RequestParam(required = false) SupplierStatementHistory.StatementType statementType,
      @RequestParam(required = false) String[] sort) {

    // Validate page size
    if (size > 100) {
      size = 100;
    }

    // Build sort object
    Sort sortObj = Sort.by(Sort.Direction.DESC, "generationDate");
    if (sort != null && sort.length > 0) {
      List<Sort.Order> orders = new java.util.ArrayList<>();
      for (String sortParam : sort) {
        String[] parts = sortParam.split(",");
        if (parts.length == 2) {
          Sort.Direction direction =
              parts[1].equalsIgnoreCase("desc") ? Sort.Direction.DESC : Sort.Direction.ASC;
          orders.add(new Sort.Order(direction, parts[0]));
        }
      }
      if (!orders.isEmpty()) {
        sortObj = Sort.by(orders);
      }
    }

    Pageable pageable = PageRequest.of(page, size, sortObj);
    Page<SupplierStatementHistoryDTO> statementsPage =
        statementService.findAllStatements(supplier, startDate, endDate, statementType, pageable);

    Map<String, Object> response = new HashMap<>();
    response.put("statements", statementsPage.getContent());
    response.put("currentPage", statementsPage.getNumber());
    response.put("totalItems", statementsPage.getTotalElements());
    response.put("totalPages", statementsPage.getTotalPages());

    return ResponseEntity.ok(response);
  }

  /**
   * Export statement to PDF or Excel.
   *
   * @param id statement ID
   * @param format export format (PDF or EXCEL)
   * @param httpRequest HTTP request for audit logging
   * @return file content as byte array
   */
  @GetMapping("/{id}/export")
  @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT', 'CFO')")
  public ResponseEntity<byte[]> exportStatement(
      @PathVariable UUID id,
      @RequestParam(required = false, defaultValue = "EXCEL")
          SupplierStatementHistory.ExportFormat format,
      HttpServletRequest httpRequest) {
    Long companyId = CompanyContext.getCompanyId();
    Long userId = SecurityUtils.getCurrentUserId();

    byte[] data = statementService.exportStatement(id, format);

    // Log audit event
    try {
      auditService.logStatementExported(companyId, userId, id, format.toString(), httpRequest);
    } catch (Exception e) {
      org.slf4j.LoggerFactory.getLogger(SupplierStatementController.class)
          .warn("Failed to log statement export: {}", e.getMessage());
    }

    HttpHeaders headers = new HttpHeaders();
    if (format == SupplierStatementHistory.ExportFormat.EXCEL) {
      headers.setContentType(
          MediaType.parseMediaType(
              "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
      headers.setContentDispositionFormData("attachment", "statement-" + id + ".xlsx");
    } else {
      headers.setContentType(MediaType.APPLICATION_PDF);
      headers.setContentDispositionFormData("attachment", "statement-" + id + ".pdf");
    }

    return ResponseEntity.ok().headers(headers).body(data);
  }

  /**
   * Send statement to supplier via email.
   *
   * @param id statement ID
   * @param recipientEmails list of recipient emails
   * @param httpRequest HTTP request for audit logging
   * @return success response
   */
  @PostMapping("/{id}/send")
  @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT', 'CFO')")
  public ResponseEntity<Map<String, String>> sendStatement(
      @PathVariable UUID id,
      @RequestBody List<String> recipientEmails,
      HttpServletRequest httpRequest) {
    Long companyId = CompanyContext.getCompanyId();
    Long userId = SecurityUtils.getCurrentUserId();

    statementService.sendStatementToSupplier(id, recipientEmails);

    // Log audit event
    try {
      auditService.logStatementSent(companyId, userId, id, recipientEmails.size(), httpRequest);
    } catch (Exception e) {
      org.slf4j.LoggerFactory.getLogger(SupplierStatementController.class)
          .warn("Failed to log statement send: {}", e.getMessage());
    }

    Map<String, String> response = new HashMap<>();
    response.put("message", "Statement sent successfully");
    response.put("recipients", String.join(", ", recipientEmails));

    return ResponseEntity.ok(response);
  }

  /**
   * Send multiple statements as ZIP attachment.
   *
   * @param statementIds list of statement IDs
   * @param recipientEmails list of recipient emails
   * @return success response
   */
  @PostMapping("/send-batch")
  @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT', 'CFO')")
  public ResponseEntity<Map<String, String>> sendBatchStatements(
      @RequestBody Map<String, Object> request) {

    @SuppressWarnings("unchecked")
    List<String> statementIdStrings = (List<String>) request.get("statementIds");
    List<UUID> statementIds = statementIdStrings.stream().map(UUID::fromString).toList();

    @SuppressWarnings("unchecked")
    List<String> recipientEmails = (List<String>) request.get("recipientEmails");

    statementService.sendBatchStatements(statementIds, recipientEmails);

    Map<String, String> response = new HashMap<>();
    response.put("message", "Batch statements sent successfully");
    response.put("count", String.valueOf(statementIds.size()));

    return ResponseEntity.ok(response);
  }

  /**
   * Download multiple statements as ZIP archive.
   *
   * @param statementIds list of statement IDs
   * @return ZIP file content
   */
  @PostMapping("/download-batch")
  @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT', 'CFO')")
  public ResponseEntity<byte[]> downloadBatchStatements(@RequestBody List<String> statementIds) {

    List<UUID> ids = statementIds.stream().map(UUID::fromString).toList();
    byte[] zipData = statementService.downloadStatementBatch(ids);

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
    headers.setContentDispositionFormData("attachment", "statements-batch.zip");

    return ResponseEntity.ok().headers(headers).body(zipData);
  }

  /**
   * Import supplier-provided statement for reconciliation.
   *
   * @param supplierId supplier ID
   * @param file statement file (Excel/CSV)
   * @param format file format (EXCEL/CSV)
   * @param httpRequest HTTP request for audit logging
   * @return reconciliation results
   */
  @PostMapping("/import")
  @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT', 'CFO')")
  public ResponseEntity<ReconciliationResultDTO> importStatement(
      @RequestParam Long supplierId,
      @RequestParam MultipartFile file,
      @RequestParam(required = false, defaultValue = "EXCEL") String format,
      HttpServletRequest httpRequest) {
    Long companyId = CompanyContext.getCompanyId();
    Long userId = SecurityUtils.getCurrentUserId();

    ReconciliationResultDTO result = statementService.importSupplierStatement(supplierId, file, format);

    // Log audit event
    try {
      auditService.logStatementImported(
          companyId,
          userId,
          supplierId,
          result.getTotalItems(),
          result.getMismatchedCount(),
          httpRequest);
    } catch (Exception e) {
      org.slf4j.LoggerFactory.getLogger(SupplierStatementController.class)
          .warn("Failed to log statement import: {}", e.getMessage());
    }

    return ResponseEntity.ok(result);
  }

  /**
   * Save reconciliation results and create disputes.
   *
   * @param request reconciliation save request with supplierId, results, and notes
   * @param httpRequest HTTP request for audit logging
   * @return success response
   */
  @PostMapping("/reconciliation/save")
  @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT', 'CFO')")
  public ResponseEntity<Map<String, String>> saveReconciliation(
      @RequestBody Map<String, Object> request,
      HttpServletRequest httpRequest) {
    Long companyId = CompanyContext.getCompanyId();
    Long userId = SecurityUtils.getCurrentUserId();

    Long supplierId = Long.valueOf(request.get("supplierId").toString());
    String notes = (String) request.get("notes");

    // Properly deserialize ReconciliationResultDTO from request
    @SuppressWarnings("unchecked")
    Map<String, Object> resultsMap = (Map<String, Object>) request.get("results");
    
    ReconciliationResultDTO results = new ReconciliationResultDTO();
    if (resultsMap != null) {
      results.setSupplierId(supplierId);
      results.setSupplierName((String) resultsMap.get("supplierName"));
      results.setReconciliationDate(
          resultsMap.get("reconciliationDate") != null
              ? LocalDate.parse(resultsMap.get("reconciliationDate").toString())
              : LocalDate.now());
      results.setTotalItems(((Number) resultsMap.getOrDefault("totalItems", 0)).intValue());
      results.setMatchedCount(((Number) resultsMap.getOrDefault("matchedCount", 0)).intValue());
      results.setMismatchedCount(((Number) resultsMap.getOrDefault("mismatchedCount", 0)).intValue());
      results.setMissingCount(((Number) resultsMap.getOrDefault("missingCount", 0)).intValue());
      results.setAppliedCount(((Number) resultsMap.getOrDefault("appliedCount", 0)).intValue());
      
      // Deserialize item lists and convert to DTOs
      @SuppressWarnings("unchecked")
      List<Map<String, Object>> matched = (List<Map<String, Object>>) resultsMap.get("matched");
      @SuppressWarnings("unchecked")
      List<Map<String, Object>> mismatched = (List<Map<String, Object>>) resultsMap.get("mismatched");
      @SuppressWarnings("unchecked")
      List<Map<String, Object>> missing = (List<Map<String, Object>>) resultsMap.get("missing");
      @SuppressWarnings("unchecked")
      List<Map<String, Object>> applied = (List<Map<String, Object>>) resultsMap.get("applied");
      
      // Convert maps to DTOs - handles all fields including billId, billNumber, billDate,
      // supplierAmount, systemAmount, variance, status, and notes
      results.setMatched(convertReconciliationItems(matched));
      results.setMismatched(convertReconciliationItems(mismatched));
      results.setMissing(convertReconciliationItems(missing));
      results.setApplied(convertReconciliationItems(applied));
    }

    statementService.saveReconciliationResults(supplierId, results, notes);

    // Log audit event
    int disputeCount = results.getMismatchedCount() + results.getMissingCount();
    try {
      auditService.logReconciliationSaved(companyId, userId, supplierId, disputeCount, httpRequest);
    } catch (Exception e) {
      org.slf4j.LoggerFactory.getLogger(SupplierStatementController.class)
          .warn("Failed to log reconciliation save: {}", e.getMessage());
    }

    Map<String, String> response = new HashMap<>();
    response.put("message", "Reconciliation results saved successfully");

    return ResponseEntity.ok(response);
  }

  private List<ReconciliationResultDTO.ReconciliationItemDTO> convertReconciliationItems(
      List<Map<String, Object>> items) {
    if (items == null) {
      return new ArrayList<>();
    }
    return items.stream()
        .map(
            item -> {
              ReconciliationResultDTO.ReconciliationItemDTO dto =
                  new ReconciliationResultDTO.ReconciliationItemDTO();
              if (item.get("billId") != null) {
                dto.setBillId(UUID.fromString(item.get("billId").toString()));
              }
              dto.setBillNumber((String) item.get("billNumber"));
              if (item.get("billDate") != null) {
                dto.setBillDate(LocalDate.parse(item.get("billDate").toString()));
              }
              if (item.get("supplierAmount") != null) {
                dto.setSupplierAmount(
                    new java.math.BigDecimal(item.get("supplierAmount").toString()));
              }
              if (item.get("systemAmount") != null) {
                dto.setSystemAmount(
                    new java.math.BigDecimal(item.get("systemAmount").toString()));
              }
              if (item.get("variance") != null) {
                dto.setVariance(new java.math.BigDecimal(item.get("variance").toString()));
              }
              dto.setStatus((String) item.get("status"));
              dto.setNotes((String) item.get("notes"));
              return dto;
            })
        .collect(java.util.stream.Collectors.toList());
  }

  /**
   * Get dispute by ID.
   *
   * @param id dispute ID
   * @return dispute DTO
   */
  @GetMapping("/disputes/{id}")
  @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT', 'CFO')")
  public ResponseEntity<SupplierStatementDisputeDTO> getDispute(@PathVariable UUID id) {
    SupplierStatementDisputeDTO dispute = statementService.getDisputeById(id);
    return ResponseEntity.ok(dispute);
  }

  /**
   * List disputes with pagination and filters.
   *
   * @param page page number (0-based, default: 0)
   * @param size page size (default: 20, max: 100)
   * @param supplier filter by supplier ID (optional)
   * @param status filter by dispute status (optional)
   * @param sort sort parameters (optional)
   * @return paginated disputes
   */
  @GetMapping("/disputes")
  @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT', 'CFO')")
  public ResponseEntity<Map<String, Object>> listDisputes(
      @RequestParam(required = false, defaultValue = "0") int page,
      @RequestParam(required = false, defaultValue = "20") int size,
      @RequestParam(required = false) Long supplier,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String[] sort) {

    // Validate page size
    if (size > 100) {
      size = 100;
    }

    // Build sort object
    Sort sortObj = Sort.by(Sort.Direction.DESC, "createdAt");
    if (sort != null && sort.length > 0) {
      List<Sort.Order> orders = new java.util.ArrayList<>();
      for (String sortParam : sort) {
        String[] parts = sortParam.split(",");
        if (parts.length == 2) {
          Sort.Direction direction =
              parts[1].equalsIgnoreCase("desc") ? Sort.Direction.DESC : Sort.Direction.ASC;
          orders.add(new Sort.Order(direction, parts[0]));
        }
      }
      if (!orders.isEmpty()) {
        sortObj = Sort.by(orders);
      }
    }

    Pageable pageable = PageRequest.of(page, size, sortObj);
    Page<SupplierStatementDisputeDTO> disputesPage =
        statementService.findDisputes(supplier, status, pageable);

    Map<String, Object> response = new HashMap<>();
    response.put("disputes", disputesPage.getContent());
    response.put("currentPage", disputesPage.getNumber());
    response.put("totalItems", disputesPage.getTotalElements());
    response.put("totalPages", disputesPage.getTotalPages());

    return ResponseEntity.ok(response);
  }

  /**
   * Update dispute status and resolution.
   *
   * @param id dispute ID
   * @param request update request with status and resolution notes
   * @return success response
   */
  @PutMapping("/disputes/{id}")
  @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT', 'CFO')")
  public ResponseEntity<Map<String, String>> updateDispute(
      @PathVariable UUID id,
      @Valid @RequestBody UpdateDisputeRequest request,
      HttpServletRequest httpRequest) {
    Long companyId = CompanyContext.getCompanyId();
    Long userId = SecurityUtils.getCurrentUserId();

    // Get old status before update
    SupplierStatementDisputeDTO oldDispute = statementService.getDisputeById(id);
    String oldStatus = oldDispute.getStatus().toString();

    statementService.updateDisputeLog(id, request);

    // Log audit event
    try {
      auditService.logDisputeUpdated(
          companyId, userId, id, oldStatus, request.getStatus().toString(), httpRequest);
    } catch (Exception e) {
      org.slf4j.LoggerFactory.getLogger(SupplierStatementController.class)
          .warn("Failed to log dispute update: {}", e.getMessage());
    }

    Map<String, String> response = new HashMap<>();
    response.put("message", "Dispute updated successfully");
    response.put("status", request.getStatus().toString());

    return ResponseEntity.ok(response);
  }

  /**
   * Get statement history for a supplier.
   *
   * @param supplierId supplier ID
   * @param page page number (0-based, default: 0)
   * @param size page size (default: 20, max: 100)
   * @return paginated statement history
   */
  @GetMapping("/history/{supplierId}")
  @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT', 'CHIEF_ACCOUNTANT', 'CFO')")
  public ResponseEntity<Map<String, Object>> getStatementHistory(
      @PathVariable Long supplierId,
      @RequestParam(required = false, defaultValue = "0") int page,
      @RequestParam(required = false, defaultValue = "20") int size) {

    if (size > 100) {
      size = 100;
    }

    Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "generationDate"));
    Page<SupplierStatementHistoryDTO> historyPage =
        statementService.getStatementHistory(supplierId, pageable);

    Map<String, Object> response = new HashMap<>();
    response.put("history", historyPage.getContent());
    response.put("currentPage", historyPage.getNumber());
    response.put("totalItems", historyPage.getTotalElements());
    response.put("totalPages", historyPage.getTotalPages());

    return ResponseEntity.ok(response);
  }
}
