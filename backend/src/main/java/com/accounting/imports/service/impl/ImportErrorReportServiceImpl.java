package com.accounting.imports.service.impl;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.accounting.entity.ImportErrorReport;
import com.accounting.imports.ImportType;
import com.accounting.imports.model.ImportRowError;
import com.accounting.imports.service.ImportErrorReportService;
import com.accounting.repository.ImportErrorReportRepository;

@Service
public class ImportErrorReportServiceImpl implements ImportErrorReportService {

  private static final String CSV_HEADER = "row_number,field,message";

  private final ImportErrorReportRepository repository;

  public ImportErrorReportServiceImpl(ImportErrorReportRepository repository) {
    this.repository = repository;
  }

  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public UUID saveReport(
      ImportType type,
      long companyId,
      long userId,
      String sourceFilename,
      List<ImportRowError> errors) {
    ImportErrorReport report = new ImportErrorReport();
    report.setId(UUID.randomUUID());
    report.setCompanyId(companyId);
    report.setImportType(type.getPathSegment());
    report.setFilename(sourceFilename);
    report.setCreatedBy(userId);
    report.setCreatedAt(Instant.now());
    report.setContent(buildCsv(errors));
    // Ensure the report is persisted immediately and independently of the caller's transaction
    repository.saveAndFlush(report);
    return report.getId();
  }

  @Override
  @Transactional(readOnly = true)
  public byte[] getReport(UUID reportId, long companyId) {
    return repository
        .findByIdAndCompanyId(reportId, companyId)
        .map(ImportErrorReport::getContent)
        .orElse(null);
  }

  private byte[] buildCsv(List<ImportRowError> errors) {
    String body =
        errors.stream()
            .map(
                error ->
                    "%d,%s,%s"
                        .formatted(
                            error.rowNumber(),
                            escape(error.field()),
                            escape(error.message())))
            .collect(Collectors.joining(System.lineSeparator()));

    String csv =
        CSV_HEADER + System.lineSeparator() + body;
    return csv.getBytes(StandardCharsets.UTF_8);
  }

  private String escape(String value) {
    if (value == null) {
      return "";
    }
    String sanitized = value.replace("\"", "\"\"");
    if (sanitized.contains(",") || sanitized.contains("\"") || sanitized.contains("\n")) {
      return "\"" + sanitized + "\"";
    }
    return sanitized;
  }
}
