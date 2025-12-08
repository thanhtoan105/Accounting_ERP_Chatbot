package com.accounting.imports.handler;

import org.springframework.web.multipart.MultipartFile;

import com.accounting.imports.model.ImportContext;
import com.accounting.imports.model.ImportSummary;

public interface ImportHandler {
  ImportSummary handle(MultipartFile file, ImportContext context);
}
