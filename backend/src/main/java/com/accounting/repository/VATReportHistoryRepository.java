package com.accounting.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.accounting.entity.VATReportHistory;

/**
 * Repository for VATReportHistory entity.
 */
public interface VATReportHistoryRepository
    extends JpaRepository<VATReportHistory, UUID>, JpaSpecificationExecutor<VATReportHistory> {

  /**
   * Find report by company ID and report ID (for company scoping).
   *
   * @param companyId company ID
   * @param reportId report ID
   * @return optional report history
   */
  java.util.Optional<VATReportHistory> findByCompanyIdAndId(Long companyId, UUID reportId);
}
