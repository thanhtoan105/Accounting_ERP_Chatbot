package com.accounting.repository;

import com.accounting.entity.AccountControl;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repository for AccountControl entity.
 * Provides company-scoped queries for account control configuration.
 */
public interface AccountControlRepository
    extends JpaRepository<AccountControl, UUID>, JpaSpecificationExecutor<AccountControl> {

  /**
   * Find account control by account ID and company ID.
   *
   * @param accountId account ID
   * @param companyId company ID
   * @return account control if present
   */
  Optional<AccountControl> findByAccountIdAndCompanyId(Long accountId, Long companyId);

  /**
   * Find all account controls for a company.
   *
   * @param companyId company ID
   * @return list of account controls
   */
  List<AccountControl> findByCompanyId(Long companyId);

  /**
   * Check if account control exists for account and company.
   *
   * @param accountId account ID
   * @param companyId company ID
   * @return true if exists, false otherwise
   */
  boolean existsByAccountIdAndCompanyId(Long accountId, Long companyId);

  /**
   * Find account controls by company and account IDs.
   *
   * @param companyId company ID
   * @param accountIds list of account IDs
   * @return list of account controls
   */
  @Query("SELECT ac FROM AccountControl ac WHERE ac.companyId = :companyId AND ac.accountId IN :accountIds")
  List<AccountControl> findByCompanyIdAndAccountIdIn(
      @Param("companyId") Long companyId, @Param("accountIds") List<Long> accountIds);
}

