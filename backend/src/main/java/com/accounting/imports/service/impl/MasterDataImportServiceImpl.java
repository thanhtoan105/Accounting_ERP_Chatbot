package com.accounting.imports.service.impl;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.accounting.imports.ImportType;
import com.accounting.imports.handler.ImportHandler;
import com.accounting.imports.model.ImportContext;
import com.accounting.imports.model.ImportSummary;
import com.accounting.imports.service.MasterDataImportService;

@Service
public class MasterDataImportServiceImpl implements MasterDataImportService {

  private final Map<ImportType, ImportHandler> handlerRegistry = new EnumMap<>(ImportType.class);

  public MasterDataImportServiceImpl(
      Map<String, ImportHandler> handlerRegistry) {
    handlerRegistry.forEach(
        (beanName, handler) -> {
          try {
            ImportType type = ImportType.fromPath(beanName);
            this.handlerRegistry.put(type, handler);
          } catch (IllegalArgumentException e) {
            // Ignore handlers that are not keyed by supported import type
          }
        });
  }

  @Override
  public ImportSummary importFile(ImportType type, MultipartFile file, ImportContext context) {
    ImportHandler handler = handlerRegistry.get(type);
    if (handler == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Unsupported import type " + type.getPathSegment());
    }
    Objects.requireNonNull(file, "File is required");
    return handler.handle(file, context);
  }
}
