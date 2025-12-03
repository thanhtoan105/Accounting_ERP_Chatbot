package com.accounting.repository.audit;

import com.accounting.entity.audit.IntegrityCheckResult;
import com.accounting.entity.audit.IntegrityCheckResult.CheckType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for integrity check results.
 */
public interface IntegrityCheckResultRepository extends JpaRepository<IntegrityCheckResult, UUID> {

  /**
   * Find the most recent check result for a company.
   */
  Optional<IntegrityCheckResult> findFirstByCompanyIdOrderByExecutedAtDesc(Long companyId);

  /**
   * Find check results for a company with pagination.
   */
  List<IntegrityCheckResult> findByCompanyIdOrderByExecutedAtDesc(Long companyId, Pageable pageable);

  /**
   * Find check results by company and type.
   */
  List<IntegrityCheckResult> findByCompanyIdAndCheckTypeOrderByExecutedAtDesc(
      Long companyId, CheckType checkType, Pageable pageable);

  /**
   * Find check result for a specific period.
   */
  Optional<IntegrityCheckResult> findByPeriodId(UUID periodId);
}
