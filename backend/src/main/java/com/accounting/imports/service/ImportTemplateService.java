package com.accounting.imports.service;

import com.accounting.imports.ImportType;

public interface ImportTemplateService {

  byte[] generateTemplate(ImportType type, String format);
}

