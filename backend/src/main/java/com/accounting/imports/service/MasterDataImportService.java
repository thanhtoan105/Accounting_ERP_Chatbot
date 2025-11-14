package com.accounting.imports.service;

import com.accounting.imports.ImportType;
import com.accounting.imports.model.ImportContext;
import com.accounting.imports.model.ImportSummary;
import org.springframework.web.multipart.MultipartFile;

public interface MasterDataImportService {

  ImportSummary importFile(ImportType type, MultipartFile file, ImportContext context);
}

