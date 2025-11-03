package com.accounting.service;

import com.accounting.entity.Company;
import java.util.List;
import java.util.Optional;

public interface CompanyService {
    Company createCompany(Company input);

    Company updateCompany(Long id, Company input);

    Optional<Company> getCompanyByCode(String code);

    List<Company> listCompanies();
}
