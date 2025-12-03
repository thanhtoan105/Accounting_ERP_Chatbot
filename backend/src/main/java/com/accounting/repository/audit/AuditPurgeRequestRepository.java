package com.accounting.repository.audit;

import com.accounting.entity.audit.AuditPurgeRequest;
import com.accounting.entity.audit.AuditPurgeRequest.PurgeStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for audit purge requests.
 */
public interface AuditPurgeRequestRepository extends JpaRepository<AuditPurgeRequest, UUID> {

  /**
   * Find all purge requests for a company, ordered by creation date.
   */
  List<AuditPurgeRequest> findByCompanyIdOrderByCreatedAtDesc(Long companyId);

  /**
   * Find pending purge requests for a company.
   */
  List<AuditPurgeRequest> findByCompanyIdAndStatus(Long companyId, PurgeStatus status);

  /**
   * Find a specific purge request by ID and company.
   */
  Optional<AuditPurgeRequest> findByIdAndCompanyId(UUID id, Long companyId);
}
