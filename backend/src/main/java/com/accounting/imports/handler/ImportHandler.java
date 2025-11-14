package com.accounting.imports.handler;

import com.accounting.imports.model.ImportContext;
import com.accounting.imports.model.ImportSummary;
import org.springframework.web.multipart.MultipartFile;

public interface ImportHandler {
  ImportSummary handle(MultipartFile file, ImportContext context);
}

