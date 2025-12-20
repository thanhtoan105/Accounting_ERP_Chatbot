package com.accounting.imports.service;

import org.springframework.web.multipart.MultipartFile;

import com.accounting.imports.ImportType;
import com.accounting.imports.model.ImportContext;
import com.accounting.imports.model.ImportSummary;

public interface MasterDataImportService {

  ImportSummary importFile(ImportType type, MultipartFile file, ImportContext context);
}
