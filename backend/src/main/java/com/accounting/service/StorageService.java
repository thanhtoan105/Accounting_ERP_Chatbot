package com.accounting.service;

import org.springframework.web.multipart.MultipartFile;

public interface StorageService {
    /**
     * Upload a company logo and return a publicly accessible URL.
     */
    String uploadCompanyLogo(Long companyId, MultipartFile file);
}
