package com.accounting.repository;

import com.accounting.entity.AuditLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {
    
    /**
     * Find audit logs by entity type, entity ID, and company ID, ordered by creation date descending.
     *
     * @param entityType entity type (e.g., "VOUCHER")
     * @param entityId   entity ID (as string)
     * @param companyId  company ID
     * @return list of audit logs ordered by creation date (newest first)
     */
    List<AuditLog> findByEntityTypeAndEntityIdAndCompanyIdOrderByCreatedAtDesc(
            String entityType, String entityId, Long companyId);

    /**
     * Find the latest audit log for a company to calculate chain hash.
     *
     * @param companyId company ID
     * @return optional latest audit log
     */
    java.util.Optional<AuditLog> findFirstByCompanyIdOrderByCreatedAtDesc(Long companyId);
}

