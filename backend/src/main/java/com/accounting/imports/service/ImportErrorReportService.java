package com.accounting.imports.service;

import com.accounting.imports.ImportType;
import com.accounting.imports.model.ImportRowError;
import java.util.List;
import java.util.UUID;

public interface ImportErrorReportService {

  UUID saveReport(
      ImportType type,
      long companyId,
      long userId,
      String sourceFilename,
      List<ImportRowError> errors);

  byte[] getReport(UUID reportId, long companyId);
}

