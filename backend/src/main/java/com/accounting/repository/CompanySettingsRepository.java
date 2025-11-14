package com.accounting.repository;

import com.accounting.entity.CompanySettings;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for CompanySettings entities.
 * CompanySettings has a one-to-one relationship with Company (unique company_id).
 */
public interface CompanySettingsRepository extends JpaRepository<CompanySettings, Long> {

  /**
   * Find company settings by company ID.
   *
   * @param companyId company ID
   * @return company settings or empty
   */
  Optional<CompanySettings> findByCompanyId(Long companyId);

  /**
   * Check if company settings exist for a company.
   *
   * @param companyId company ID
   * @return true if settings exist, false otherwise
   */
  boolean existsByCompanyId(Long companyId);
}

