package com.accounting.imports.exception;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.accounting.imports.model.ImportRowAudit;
import com.accounting.imports.model.ImportRowError;

public class ImportValidationException extends ResponseStatusException {

  private final transient List<ImportRowError> errors;
  private final java.util.UUID reportId;
  private final transient List<ImportRowAudit> auditEntries;

  public ImportValidationException(String message, List<ImportRowError> errors) {
    this(message, errors, null, List.of());
  }

  public ImportValidationException(
      String message, List<ImportRowError> errors, java.util.UUID reportId) {
    this(message, errors, reportId, List.of());
  }

  public ImportValidationException(
      String message,
      List<ImportRowError> errors,
      java.util.UUID reportId,
      List<ImportRowAudit> auditEntries) {
    super(HttpStatus.BAD_REQUEST, message);
    this.errors = errors;
    this.reportId = reportId;
    this.auditEntries = auditEntries == null ? List.of() : List.copyOf(auditEntries);
  }

  public List<ImportRowError> getErrors() {
    return errors;
  }

  public java.util.UUID getReportId() {
    return reportId;
  }

  public List<ImportRowAudit> getAuditEntries() {
    return auditEntries;
  }
}
