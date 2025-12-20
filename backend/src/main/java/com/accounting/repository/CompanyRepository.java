package com.accounting.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.accounting.entity.Company;

public interface CompanyRepository extends JpaRepository<Company, Long> {
    Optional<Company> findByCode(String code);

    boolean existsByCode(String code);

    boolean existsByTaxCode(String taxCode);
}
