package com.accounting.imports.service;

import java.util.List;
import java.util.UUID;

import com.accounting.imports.ImportType;
import com.accounting.imports.model.ImportRowError;

public interface ImportErrorReportService {

  UUID saveReport(
      ImportType type,
      long companyId,
      long userId,
      String sourceFilename,
      List<ImportRowError> errors);

  byte[] getReport(UUID reportId, long companyId);
}
