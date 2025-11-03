package com.accounting.repository;

import com.accounting.entity.Company;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyRepository extends JpaRepository<Company, Long> {
    Optional<Company> findByCode(String code);

    boolean existsByCode(String code);

    boolean existsByTaxCode(String taxCode);
}
