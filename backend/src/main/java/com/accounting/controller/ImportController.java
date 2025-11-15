package com.accounting.controller;

import com.accounting.dto.ImportResultDTO;
import com.accounting.imports.ImportType;
import com.accounting.imports.exception.ImportValidationException;
import com.accounting.imports.model.ImportSummary;
import com.accounting.imports.service.ImportErrorReportService;
import com.accounting.imports.service.ImportTemplateService;
import com.accounting.imports.service.MasterDataImportFacade;
import com.accounting.security.CompanyContext;
import com.accounting.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/import")
public class ImportController {

  private final MasterDataImportFacade importFacade;
  private final ImportTemplateService templateService;
  private final ImportErrorReportService errorReportService;
  private final AuditService auditService;

  public ImportController(
      MasterDataImportFacade importFacade,
      ImportTemplateService templateService,
      ImportErrorReportService errorReportService,
      AuditService auditService) {
    this.importFacade = importFacade;
    this.templateService = templateService;
    this.errorReportService = errorReportService;
    this.auditService = auditService;
  }

  @PostMapping("/{type}")
  @PreAuthorize("hasAnyRole('ADMIN','CHIEF_ACCOUNTANT')")
  public ResponseEntity<ImportResultDTO> importData(
      @PathVariable("type") String type,
      @RequestPart("file") MultipartFile file,
      @RequestParam(value = "locale", required = false, defaultValue = "en") String locale,
      HttpServletRequest request) {
    ImportType importType = ImportType.fromPath(type);
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Missing company context (X-Company-Id header required)");
    }
    Long userId = resolveCurrentUserId();
    if (userId == null) {
      throw new ResponseStatusException(
          HttpStatus.UNAUTHORIZED, "Authenticated user is required");
    }

    ImportResultDTO result = importFacade.process(importType, file, locale, request);
    return ResponseEntity.ok(result);
  }

  @GetMapping("/templates/{type}")
  @PreAuthorize("hasAnyRole('ADMIN','CHIEF_ACCOUNTANT')")
  public ResponseEntity<byte[]> downloadTemplate(
      @PathVariable("type") String type,
      @RequestParam(value = "format", defaultValue = "xlsx") String format) {
    ImportType importType = ImportType.fromPath(type);
    String normalizedFormat = format.toLowerCase(Locale.ROOT);
    if (!List.of("csv", "xls", "xlsx").contains(normalizedFormat)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Supported formats: csv, xls, xlsx");
    }
    byte[] payload = templateService.generateTemplate(importType, normalizedFormat);
    String extension = normalizedFormat;
    if ("xls".equals(extension) || "xlsx".equals(extension)) {
      extension = "xlsx".equals(extension) ? "xlsx" : "xls";
    }
    String filename = importType.getTemplateBaseName() + "." + extension;

    MediaType mediaType = switch (normalizedFormat) {
      case "csv" -> MediaType.parseMediaType("text/csv");
      case "xls" ->
        MediaType.parseMediaType("application/vnd.ms-excel");
      default ->
        MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    };

    return ResponseEntity.ok()
        .header(
            "Content-Disposition",
            "attachment; filename=\"" + filename + "\"")
        .contentType(mediaType)
        .body(payload);
  }

  @GetMapping("/error-reports/{reportId}")
  @PreAuthorize("hasAnyRole('ADMIN','CHIEF_ACCOUNTANT')")
  public ResponseEntity<byte[]> downloadErrorReport(@PathVariable("reportId") String reportId) {
    Long companyId = CompanyContext.getCompanyId();
    if (companyId == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Missing company context (X-Company-Id header required)");
    }
    UUID id;
    try {
      id = UUID.fromString(reportId);
    } catch (IllegalArgumentException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid report identifier");
    }
    byte[] content = errorReportService.getReport(id, companyId);
    if (content == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Error report not found");
    }
    return ResponseEntity.ok()
        .header(
            "Content-Disposition",
            "attachment; filename=\"import-error-report-" + id + ".csv\"")
        .contentType(MediaType.TEXT_PLAIN)
        .body(content);
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

  private void logAudit(
      ImportType type, ImportSummary summary, Long userId, HttpServletRequest request) {
    switch (type) {
      case CUSTOMERS -> auditService.logCustomerImport(
          summary.getSuccessCount(), summary.getErrorCount(), userId, request);
      case SUPPLIERS -> auditService.logSupplierImport(
          summary.getSuccessCount(), summary.getErrorCount(), userId, request);
      case BANK_ACCOUNTS -> auditService.logBankAccountImport(
          summary.getSuccessCount(), summary.getErrorCount(), userId, request);
      default -> {
      }
    }
  }

  private void logAuditFailure(
      ImportType type, ImportValidationException exception, Long userId, HttpServletRequest request) {
    int errorCount = exception.getErrors() != null ? exception.getErrors().size() : 0;
    switch (type) {
      case CUSTOMERS -> auditService.logCustomerImport(0, errorCount, userId, request);
      case SUPPLIERS -> auditService.logSupplierImport(0, errorCount, userId, request);
      case BANK_ACCOUNTS -> auditService.logBankAccountImport(0, errorCount, userId, request);
      default -> {
      }
    }
  }

}
