package com.accounting.imports.service;

import com.accounting.dto.ImportResultDTO;
import com.accounting.imports.ImportType;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.multipart.MultipartFile;

public interface MasterDataImportFacade {

  ImportResultDTO process(
      ImportType type, MultipartFile file, String locale, HttpServletRequest request);
}

