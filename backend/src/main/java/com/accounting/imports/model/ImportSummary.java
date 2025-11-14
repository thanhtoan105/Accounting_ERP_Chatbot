package com.accounting.imports.model;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class ImportSummary {

  private final int successCount;
  private final int skippedCount;
  private final int errorCount;
  private final List<ImportRowError> errors;
  private final UUID errorReportId;
  private final List<ImportRowAudit> auditEntries;

  public ImportSummary(
      int successCount,
      int skippedCount,
      int errorCount,
      List<ImportRowError> errors,
      UUID errorReportId,
      List<ImportRowAudit> auditEntries) {
    this.successCount = successCount;
    this.skippedCount = skippedCount;
    this.errorCount = errorCount;
    this.errors = errors == null ? List.of() : List.copyOf(errors);
    this.errorReportId = errorReportId;
    this.auditEntries = auditEntries == null ? List.of() : List.copyOf(auditEntries);
  }

  public static ImportSummary success(int successCount) {
    return success(successCount, Collections.emptyList());
  }

  public static ImportSummary success(int successCount, List<ImportRowAudit> auditEntries) {
    return new ImportSummary(successCount, 0, 0, Collections.emptyList(), null, auditEntries);
  }

  public static ImportSummary failure(List<ImportRowError> errors, UUID reportId) {
    return failure(errors, reportId, Collections.emptyList());
  }

  public static ImportSummary failure(
      List<ImportRowError> errors, UUID reportId, List<ImportRowAudit> auditEntries) {
    int errorCount = errors == null ? 0 : errors.size();
    return new ImportSummary(0, 0, errorCount, errors, reportId, auditEntries);
  }

  public int getSuccessCount() {
    return successCount;
  }

  public int getSkippedCount() {
    return skippedCount;
  }

  public int getErrorCount() {
    return errorCount;
  }

  public List<ImportRowError> getErrors() {
    return errors;
  }

  public UUID getErrorReportId() {
    return errorReportId;
  }

  public List<ImportRowAudit> getAuditEntries() {
    return auditEntries;
  }
}
