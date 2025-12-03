package com.accounting.controller.audit;

import com.accounting.dto.audit.CashAuditPageDTO;
import com.accounting.dto.audit.CashAuditQueryDTO;
import com.accounting.dto.audit.IntegrityCheckResultDTO;
import com.accounting.dto.audit.PurgeRequestDTO;
import com.accounting.dto.audit.PurgeResponseDTO;
import com.accounting.security.CompanyContext;
import com.accounting.service.CashAuditService;
import com.accounting.service.CashAuditService.ExportFormat;
import com.accounting.service.IntegrityCheckService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for Cash & Bank audit operations.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>GET /api/v1/audit/cash-bank - Query audit logs</li>
 *   <li>GET /api/v1/audit/cash-bank/export - Export audit logs</li>
 *   <li>POST /api/v1/audit/cash-bank/purge/request - Request purge</li>
 *   <li>POST /api/v1/audit/cash-bank/purge/{requestId}/approve - Approve purge</li>
 *   <li>POST /api/v1/audit/cash-bank/purge/{requestId}/reject - Reject purge</li>
 *   <li>GET /api/v1/audit/cash-bank/integrity-checks - List integrity checks</li>
 *   <li>POST /api/v1/audit/cash-bank/integrity-checks/run - Trigger manual check</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/audit/cash-bank")
@Tag(name = "Cash & Bank Audit", description = "Audit log queries, exports, and compliance operations")
public class CashAuditController {

  private static final Logger logger = LoggerFactory.getLogger(CashAuditController.class);
  private static final DateTimeFormatter FILENAME_TS = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

  private final CashAuditService cashAuditService;
  private final IntegrityCheckService integrityCheckService;

  public CashAuditController(
      CashAuditService cashAuditService,
      IntegrityCheckService integrityCheckService) {
    this.cashAuditService = cashAuditService;
    this.integrityCheckService = integrityCheckService;
  }

  // === Audit Log Query Endpoints ===

  @GetMapping
  @PreAuthorize("hasAnyRole('ADMIN', 'CHIEF_ACCOUNTANT')")
  @Operation(summary = "Query audit logs", description = "Query audit logs with filters and pagination")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Successful query"),
      @ApiResponse(responseCode = "400", description = "Invalid date range or missing company context"),
      @ApiResponse(responseCode = "403", description = "Access denied")
  })
  public ResponseEntity<CashAuditPageDTO> queryAuditLogs(
      @Parameter(description = "Start date (ISO-8601)", required = true)
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
      @Parameter(description = "End date (ISO-8601)", required = true)
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
      @Parameter(description = "Bank account ID")
      @RequestParam(required = false) Long bankAccountId,
      @Parameter(description = "Action types (multi-select)")
      @RequestParam(required = false) List<String> actionType,
      @Parameter(description = "User ID")
      @RequestParam(required = false) Long userId,
      @Parameter(description = "Entity type")
      @RequestParam(required = false) String entityType,
      @Parameter(description = "Page number (0-based)")
      @RequestParam(defaultValue = "0") int page,
      @Parameter(description = "Page size (10, 20, or 50)")
      @RequestParam(defaultValue = "20") int size) {

    logger.debug("Query audit logs: {} to {}, page={}, size={}", dateFrom, dateTo, page, size);

    CashAuditQueryDTO filter = new CashAuditQueryDTO();
    filter.setDateFrom(dateFrom);
    filter.setDateTo(dateTo);
    filter.setBankAccountId(bankAccountId);
    filter.setActionTypes(actionType);
    filter.setUserId(userId);
    filter.setEntityType(entityType);
    filter.setPage(page);
    filter.setSize(size);

    CashAuditPageDTO result = cashAuditService.queryAuditLogs(filter);
    return ResponseEntity.ok(result);
  }

  @GetMapping("/export")
  @PreAuthorize("hasAnyRole('ADMIN', 'CHIEF_ACCOUNTANT')")
  @Operation(summary = "Export audit logs", description = "Export audit logs to JSON, CSV, or PDF")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Export successful",
          content = @Content(mediaType = "application/octet-stream")),
      @ApiResponse(responseCode = "400", description = "Export limit exceeded (10,000 records)"),
      @ApiResponse(responseCode = "403", description = "Access denied")
  })
  public ResponseEntity<byte[]> exportAuditLogs(
      @Parameter(description = "Start date (ISO-8601)", required = true)
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
      @Parameter(description = "End date (ISO-8601)", required = true)
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
      @Parameter(description = "Export format", required = true)
      @RequestParam ExportFormat format,
      @Parameter(description = "Bank account ID")
      @RequestParam(required = false) Long bankAccountId,
      @Parameter(description = "Action types")
      @RequestParam(required = false) List<String> actionType,
      @Parameter(description = "User ID")
      @RequestParam(required = false) Long userId,
      @Parameter(description = "Entity type")
      @RequestParam(required = false) String entityType) {

    logger.info("Exporting audit logs: {} to {} as {}", dateFrom, dateTo, format);

    CashAuditQueryDTO filter = new CashAuditQueryDTO();
    filter.setDateFrom(dateFrom);
    filter.setDateTo(dateTo);
    filter.setBankAccountId(bankAccountId);
    filter.setActionTypes(actionType);
    filter.setUserId(userId);
    filter.setEntityType(entityType);
    filter.setPage(0);
    filter.setSize(10000); // Max export

    byte[] content = cashAuditService.exportAuditLogs(filter, format);
    String hash = cashAuditService.getLastExportHash();

    String timestamp = java.time.OffsetDateTime.now().format(FILENAME_TS);
    String filename = String.format("audit-export-%s.%s", timestamp, format.name().toLowerCase());

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(getMediaType(format));
    headers.setContentDispositionFormData("attachment", filename);
    headers.set("X-Content-SHA256", hash != null ? hash : "N/A");
    headers.set("X-Record-Count", String.valueOf(content.length));

    return ResponseEntity.ok()
        .headers(headers)
        .body(content);
  }

  // === Purge Endpoints ===

  @PostMapping("/purge/request")
  @PreAuthorize("hasRole('CHIEF_ACCOUNTANT')")
  @Operation(summary = "Request audit log purge", description = "Request purge of audit logs (requires Admin approval)")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Purge request created"),
      @ApiResponse(responseCode = "400", description = "Invalid request"),
      @ApiResponse(responseCode = "403", description = "Requires CHIEF_ACCOUNTANT role")
  })
  public ResponseEntity<PurgeResponseDTO> requestPurge(
      @Valid @RequestBody PurgeRequestDTO request) {

    logger.info("Purge request received for dates {} to {}", request.getDateFrom(), request.getDateTo());

    PurgeResponseDTO response = cashAuditService.requestPurge(request);
    return ResponseEntity.ok(response);
  }

  @PostMapping("/purge/{requestId}/approve")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Approve purge request", description = "Approve and execute audit log purge (Admin only, must be different user)")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Purge approved and executed"),
      @ApiResponse(responseCode = "400", description = "Self-approval forbidden"),
      @ApiResponse(responseCode = "403", description = "Requires ADMIN role"),
      @ApiResponse(responseCode = "404", description = "Purge request not found"),
      @ApiResponse(responseCode = "409", description = "Request already processed")
  })
  public ResponseEntity<PurgeResponseDTO> approvePurge(
      @PathVariable UUID requestId) {

    logger.info("Approving purge request {}", requestId);

    PurgeResponseDTO response = cashAuditService.approvePurge(requestId);
    return ResponseEntity.ok(response);
  }

  @PostMapping("/purge/{requestId}/reject")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Reject purge request", description = "Reject audit log purge request")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Purge request rejected"),
      @ApiResponse(responseCode = "403", description = "Requires ADMIN role"),
      @ApiResponse(responseCode = "404", description = "Purge request not found")
  })
  public ResponseEntity<PurgeResponseDTO> rejectPurge(
      @PathVariable UUID requestId,
      @RequestParam String reason) {

    logger.info("Rejecting purge request {} with reason: {}", requestId, reason);

    PurgeResponseDTO response = cashAuditService.rejectPurge(requestId, reason);
    return ResponseEntity.ok(response);
  }

  @GetMapping("/purge/{requestId}")
  @PreAuthorize("hasAnyRole('ADMIN', 'CHIEF_ACCOUNTANT')")
  @Operation(summary = "Get purge request", description = "Get purge request details")
  public ResponseEntity<PurgeResponseDTO> getPurgeRequest(@PathVariable UUID requestId) {
    PurgeResponseDTO response = cashAuditService.getPurgeRequest(requestId);
    if (response == null) {
      return ResponseEntity.notFound().build();
    }
    return ResponseEntity.ok(response);
  }

  // === Integrity Check Endpoints ===

  @GetMapping("/integrity-checks")
  @PreAuthorize("hasAnyRole('ADMIN', 'CHIEF_ACCOUNTANT')")
  @Operation(summary = "List integrity checks", description = "Get integrity check history")
  public ResponseEntity<List<IntegrityCheckResultDTO>> listIntegrityChecks(
      @RequestParam(defaultValue = "20") int limit) {

    Long companyId = CompanyContext.getCompanyId();
    List<IntegrityCheckResultDTO> results = integrityCheckService.getCheckHistory(companyId, limit);
    return ResponseEntity.ok(results);
  }

  @GetMapping("/integrity-checks/last")
  @PreAuthorize("hasAnyRole('ADMIN', 'CHIEF_ACCOUNTANT')")
  @Operation(summary = "Get last integrity check", description = "Get the most recent integrity check result")
  public ResponseEntity<IntegrityCheckResultDTO> getLastIntegrityCheck() {
    Long companyId = CompanyContext.getCompanyId();
    IntegrityCheckResultDTO result = integrityCheckService.getLastCheckResult(companyId);
    if (result == null) {
      return ResponseEntity.noContent().build();
    }
    return ResponseEntity.ok(result);
  }

  @PostMapping("/integrity-checks/run")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Run integrity check", description = "Trigger manual integrity check (Admin only)")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Check completed"),
      @ApiResponse(responseCode = "403", description = "Requires ADMIN role"),
      @ApiResponse(responseCode = "409", description = "Check already in progress")
  })
  public ResponseEntity<IntegrityCheckResultDTO> runIntegrityCheck() {
    Long companyId = CompanyContext.getCompanyId();
    logger.info("Manual integrity check triggered for company {}", companyId);

    IntegrityCheckResultDTO result = integrityCheckService.runDailyIntegrityCheck(companyId);

    return ResponseEntity.ok(result);
  }

  // === Helper Methods ===

  private MediaType getMediaType(ExportFormat format) {
    return switch (format) {
      case JSON -> MediaType.APPLICATION_JSON;
      case CSV -> new MediaType("text", "csv");
      case PDF -> MediaType.APPLICATION_PDF;
    };
  }
}
