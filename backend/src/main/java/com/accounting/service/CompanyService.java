package com.accounting.service;

import java.util.List;
import java.util.Optional;

import org.springframework.web.multipart.MultipartFile;

import com.accounting.dto.UpdateBasicCompanySettingsRequest;
import com.accounting.entity.Company;

public interface CompanyService {
    Company createCompany(Company input);

    Company updateCompany(Long id, Company input);

    Optional<Company> getCompanyByCode(String code);

    List<Company> listCompanies();

    /** Retrieve settings for the current company from context */
    Company getCurrentCompanySettings();

    /** Update settings for the current company (optionally with logo upload handled elsewhere) */
    Company updateCurrentCompanySettings(UpdateBasicCompanySettingsRequest request, MultipartFile logoFile);
}
