package com.accounting.imports.service;

import org.springframework.web.multipart.MultipartFile;

import com.accounting.dto.ImportResultDTO;
import com.accounting.imports.ImportType;

import jakarta.servlet.http.HttpServletRequest;

public interface MasterDataImportFacade {

  ImportResultDTO process(
      ImportType type, MultipartFile file, String locale, HttpServletRequest request);
}
