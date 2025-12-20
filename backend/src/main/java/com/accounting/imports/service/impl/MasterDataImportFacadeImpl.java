package com.accounting.imports.service.impl;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.accounting.dto.ImportResultDTO;
import com.accounting.dto.ImportRowErrorDTO;
import com.accounting.imports.ImportType;
import com.accounting.imports.exception.ImportValidationException;
import com.accounting.imports.model.ImportContext;
import com.accounting.imports.model.ImportRowAudit;
import com.accounting.imports.model.ImportRowError;
import com.accounting.imports.model.ImportSummary;
import com.accounting.imports.service.MasterDataImportFacade;
import com.accounting.imports.service.MasterDataImportService;
import com.accounting.security.CompanyContext;
import com.accounting.service.AuditService;

import jakarta.servlet.http.HttpServletRequest;

@Component
public class MasterDataImportFacadeImpl implements MasterDataImportFacade {

  private final MasterDataImportService importService;
  private final AuditService auditService;

  public MasterDataImportFacadeImpl(
      MasterDataImportService importService, AuditService auditService) {
    this.importService = importService;
    this.auditService = auditService;
  }

  @Override
  public ImportResultDTO process(
      ImportType type, MultipartFile file, String locale, HttpServletRequest request) {
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

    String ipAddress = request != null ? request.getRemoteAddr() : null;
    String userAgent = request != null ? request.getHeader("User-Agent") : null;
    UUID attemptId = UUID.randomUUID();
    ImportContext context = new ImportContext(
        companyId,
        userId,
        file.getOriginalFilename(),
        Instant.now(),
        locale,
        attemptId,
        ipAddress,
        userAgent);

    try {
      ImportSummary summary = importService.importFile(
          type,
          file,
          context);
      logAudit(type, summary, userId, request, context);
      return toDto(summary);
    } catch (ImportValidationException exception) {
      logAuditFailure(type, exception, userId, request, context);
      return toDto(exception);
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

  private void logAudit(
      ImportType type,
      ImportSummary summary,
      Long userId,
      HttpServletRequest request,
      ImportContext context) {
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
    recordRowAuditEntries(type, context, userId, summary.getAuditEntries());
  }

  private void logAuditFailure(
      ImportType type,
      ImportValidationException exception,
      Long userId,
      HttpServletRequest request,
      ImportContext context) {
    int errorCount = exception.getErrors() != null ? exception.getErrors().size() : 0;
    switch (type) {
      case CUSTOMERS -> auditService.logCustomerImport(0, errorCount, userId, request);
      case SUPPLIERS -> auditService.logSupplierImport(0, errorCount, userId, request);
      case BANK_ACCOUNTS -> auditService.logBankAccountImport(0, errorCount, userId, request);
      default -> {
      }
    }
    recordRowAuditEntries(type, context, userId, exception.getAuditEntries());
  }

  private ImportResultDTO toDto(ImportSummary summary) {
    List<ImportRowErrorDTO> errors = summary.getErrors().stream()
        .map(this::toDto)
        .collect(Collectors.toList());
    return new ImportResultDTO(
        summary.getSuccessCount(),
        summary.getSkippedCount(),
        summary.getErrorCount(),
        errors,
        summary.getErrorReportId());
  }

  private ImportResultDTO toDto(ImportValidationException exception) {
    List<ImportRowErrorDTO> errors = exception.getErrors().stream()
        .map(this::toDto)
        .collect(Collectors.toList());
    return new ImportResultDTO(
        0, 0, errors.size(), errors, exception.getReportId());
  }

  private ImportRowErrorDTO toDto(ImportRowError error) {
    return new ImportRowErrorDTO(
        error.rowNumber(), error.field(), error.message());
  }

  private void recordRowAuditEntries(
      ImportType type,
      ImportContext context,
      Long userId,
      List<ImportRowAudit> entries) {
    if (entries == null || entries.isEmpty()) {
      return;
    }
    for (ImportRowAudit entry : entries) {
      auditService.logImportRow(
          context.companyId(),
          userId,
          type,
          context.attemptId(),
          context.filename(),
          entry.rowNumber(),
          entry.status(),
          entry.beforePayload(),
          entry.afterPayload(),
          entry.message(),
          context.ipAddress(),
          context.userAgent());
    }
  }
}
