package com.accounting.controller.admin;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.accounting.dto.audit.AuditLogFilter;
import com.accounting.dto.audit.AuditLogPageResponse;
import com.accounting.security.CompanyContext;
import com.accounting.service.AuditService;
import com.accounting.service.impl.AuditLogExportService;
import com.accounting.service.impl.AuditLogQueryService;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/v1/admin/audit-logs")
public class AuditLogController {

  private final AuditLogQueryService queryService;
  private final AuditLogExportService exportService;
  private final AuditService auditService;

  public AuditLogController(
      AuditLogQueryService queryService,
      AuditLogExportService exportService,
      AuditService auditService) {
    this.queryService = queryService;
    this.exportService = exportService;
    this.auditService = auditService;
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('ADMIN','CHIEF_ACCOUNTANT')")
  public ResponseEntity<Map<String, Object>> listAuditLogs(
      @RequestParam(value = "entityType", required = false) String entityType,
      @RequestParam(value = "entityId", required = false) String entityId,
      @RequestParam(value = "action", required = false) String action,
      @RequestParam(value = "eventType", required = false) String eventType,
      @RequestParam(value = "userEmail", required = false) String userEmail,
      @RequestParam(value = "actorRole", required = false) String actorRole,
      @RequestParam(value = "success", required = false) String successParam,
      @RequestParam(value = "from", required = false) String from,
      @RequestParam(value = "to", required = false) String to,
      @RequestParam(value = "page", defaultValue = "0") int page,
      @RequestParam(value = "size", defaultValue = "20") int size) {
    Long companyId = requireCompanyContext();
    Boolean success = parseBoolean(successParam);
    Instant fromInstant = parseInstant(from, "from");
    Instant toInstant = parseInstant(to, "to");

    AuditLogFilter filter = new AuditLogFilter(
        companyId,
        entityType,
        entityId,
        action,
        eventType,
        userEmail,
        actorRole,
        success,
        fromInstant,
        toInstant,
        page,
        size);

    AuditLogPageResponse response = queryService.search(filter);
    Map<String, Object> body = new HashMap<>();
    body.put("data", response.data());
    body.put("meta", response.meta());
    return ResponseEntity.ok(body);
  }

  @GetMapping("/export")
  @PreAuthorize("hasAnyRole('ADMIN','CHIEF_ACCOUNTANT')")
  public ResponseEntity<byte[]> exportAuditLogs(
      @RequestParam(value = "entityType", required = false) String entityType,
      @RequestParam(value = "entityId", required = false) String entityId,
      @RequestParam(value = "action", required = false) String action,
      @RequestParam(value = "eventType", required = false) String eventType,
      @RequestParam(value = "userEmail", required = false) String userEmail,
      @RequestParam(value = "actorRole", required = false) String actorRole,
      @RequestParam(value = "success", required = false) String successParam,
      @RequestParam(value = "from", required = false) String from,
      @RequestParam(value = "to", required = false) String to,
      HttpServletRequest httpRequest) {
    Long companyId = requireCompanyContext();
    Boolean success = parseBoolean(successParam);
    Instant fromInstant = parseInstant(from, "from");
    Instant toInstant = parseInstant(to, "to");

    AuditLogFilter filter = new AuditLogFilter(
        companyId,
        entityType,
        entityId,
        action,
        eventType,
        userEmail,
        actorRole,
        success,
        fromInstant,
        toInstant,
        0,
        AuditLogExportService.EXPORT_LIMIT());

    AuditLogExportService.AuditLogExportResult result = exportService.export(filter);

    auditService.logAuditExport(
        companyId,
        resolveCurrentUserId(),
        result.count(),
        "csv",
        result.hash(),
        httpRequest);

    HttpHeaders headers = new HttpHeaders();
    headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + result.filename() + "\"");
    headers.set("X-Content-SHA256", result.hash());
    headers.setContentType(MediaType.parseMediaType("text/csv"));
    return ResponseEntity.ok()
        .headers(headers)
        .body(result.content());
  }

  private Long requireCompanyContext() {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(
          org.springframework.http.HttpStatus.BAD_REQUEST,
          "Missing company context (X-Company-Id header required)");
    }
    return companyId;
  }

  private Boolean parseBoolean(String value) {
    if (!StringUtils.hasText(value)) {
      return null;
    }
    return switch (value.trim().toLowerCase()) {
      case "true", "1", "yes" -> Boolean.TRUE;
      case "false", "0", "no" -> Boolean.FALSE;
      default -> null;
    };
  }

  private Instant parseInstant(String value, String field) {
    if (!StringUtils.hasText(value)) {
      return null;
    }
    try {
      return Instant.parse(value);
    } catch (DateTimeParseException ex) {
      throw new ResponseStatusException(
          org.springframework.http.HttpStatus.BAD_REQUEST,
          "Invalid " + field + " value. Expected ISO-8601 instant.");
    }
  }

  private Long resolveCurrentUserId() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || authentication.getPrincipal() == null) {
      return null;
    }
    try {
      return Long.parseLong(authentication.getPrincipal().toString());
    } catch (NumberFormatException ex) {
      return null;
    }
  }
}
